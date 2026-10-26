package com.mymail.integration.google.service;

import com.mymail.api.model.GoogleAuthorizationResponse;
import com.mymail.api.model.GoogleAuthorizeRequest;
import com.mymail.api.model.GoogleCompleteRequest;
import com.mymail.api.model.GoogleConnectionResponse;
import com.mymail.authentication.service.AuthenticationRateLimiter;
import com.mymail.common.ApiException;
import com.mymail.email.repository.GoogleEmailRepository;
import com.mymail.integration.google.entities.GoogleAccount;
import com.mymail.integration.google.mappers.GoogleAccountMapper;
import com.mymail.integration.google.repository.GoogleAccountRepository;
import com.mymail.integration.google.repository.GoogleOAuthStateRepository;
import com.mymail.user.entities.User;
import com.mymail.user.repository.UserRepository;
import com.mymail.user.service.UserService;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.mymail.integration.google.GoogleOAuthConstants.ACCESS_TOKEN;
import static com.mymail.integration.google.GoogleOAuthConstants.REFRESH_TOKEN;
import static com.mymail.integration.google.GoogleOAuthConstants.SCOPE;

@Service
@RequiredArgsConstructor
public class GoogleAccountService {
    private static final String AUTHORIZATION_RATE_LIMIT_KEY = "google-authorize";
    private static final int AUTHORIZATION_RATE_LIMIT = 10;
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofSeconds(30);
    private static final Duration REVOCATION_RETRY_DELAY = Duration.ofMinutes(1);

    private final GoogleAccountRepository accounts;
    private final GoogleAccountMapper mapper;
    private final GoogleOAuthStateRepository states;
    private final OAuthStateService transactions;
    private final GoogleOAuthService provider;
    private final UserService users;
    private final UserRepository userRepository;
    private final GoogleSettings settings;
    private final AuthenticationRateLimiter limiter;
    private final GoogleEmailRepository cachedEmails;

    @Transactional
    public GoogleAuthorizationResponse authorize(Authentication authentication, GoogleAuthorizeRequest request) {
        validateIntegrationRequest(request.getRedirectUri());
        User user = lockUser(authentication);
        limiter.check(AUTHORIZATION_RATE_LIMIT_KEY, user.getIdpId(), AUTHORIZATION_RATE_LIMIT);
        requireNoPendingDisconnect(accounts.findByUserId(user.getId()).orElse(null));

        var transaction = transactions.create(user, request.getRedirectUri(), request.getCodeChallenge());
        String authorizationUrl = provider.createAuthorizationUrl(
                transaction.getState(), request.getRedirectUri(), request.getCodeChallenge());
        return mapper.toAuthorizationResponse(transaction.getState(), URI.create(authorizationUrl));
    }

    @Transactional
    public GoogleConnectionResponse complete(Authentication authentication, GoogleCompleteRequest request) {
        validateIntegrationRequest(request.getRedirectUri());
        User user = lockUser(authentication);
        GoogleAccount existing = accounts.findByUserId(user.getId()).orElse(null);
        requireNoPendingDisconnect(existing);

        var transaction = transactions.validateAndConsume(
                request.getState(), request.getCodeVerifier(), request.getRedirectUri(), user);
        Map<?, ?> token = exchangeAuthorizationCode(request, transaction.getRedirectUri());
        String email = callbackEmail(token);
        requireAccountOwnership(email, user);
        String refreshToken = resolveRefreshToken(token, existing, email);
        revokeReplacedAccount(existing, email);

        GoogleAccount account = saveConnection(existing, user, email, token, refreshToken);
        return mapper.toResponse(account);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public GoogleConnectionResponse status(Authentication authentication) {
        settings.requireEnabled();
        User user = lockUser(authentication);
        GoogleAccount account = accounts.findByUserId(user.getId()).orElse(null);
        if (account == null) {
            return mapper.disconnected();
        }
        checkConnectionHealth(account);
        return mapper.toResponse(account);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public GoogleAccount getByAuthentication(Authentication authentication) {
        settings.requireEnabled();
        User user = lockUser(authentication);
        GoogleAccount account = accounts.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GOOGLE_DISCONNECTED"));
        checkConnectionHealth(account);
        if (requiresReconnect(account)) {
            throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_RECONNECT_REQUIRED");
        }
        return account;
    }

    /** Local ownership and connection-state checks only; cached reads never contact Google. */
    @Transactional(readOnly = true)
    public GoogleAccount getCachedAccountByAuthentication(Authentication authentication) {
        settings.requireEnabled();
        if (!(authentication instanceof JwtAuthenticationToken) || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("A validated application identity is required");
        }
        GoogleAccount account = accounts.findByUser_IdpId(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GOOGLE_DISCONNECTED"));
        if (requiresReconnect(account) || !GoogleSettings.SCOPE.equals(account.getGrantedScope())) {
            throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_RECONNECT_REQUIRED");
        }
        return account;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void disconnect(Authentication authentication) {
        User user = lockUser(authentication);
        states.cancel(user.getId());
        GoogleAccount account = accounts.findByUserId(user.getId()).orElse(null);
        if (account == null) {
            return;
        }

        // Persist before contacting Google so outages cannot leave local credentials usable.
        account.setRevocationPending(true);
        account.setReconnectRequired(true);
        accounts.saveAndFlush(account);
        revokeAndRemove(account);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void retryDisconnect(UUID userId) {
        userRepository.lockById(userId).orElse(null);
        accounts.findByUserId(userId)
                .filter(GoogleAccount::isRevocationPending)
                .ifPresent(this::revokeAndRemove);
    }

    private User lockUser(Authentication authentication) {
        User user = users.getOrCreateUser(authentication);
        return userRepository.lockById(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "USER_UNAVAILABLE"));
    }

    private void validateIntegrationRequest(String redirectUri) {
        settings.requireEnabled();
        settings.checkRedirect(redirectUri);
    }

    private void requireNoPendingDisconnect(GoogleAccount account) {
        if (account != null && account.isRevocationPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCONNECT_PENDING");
        }
    }

    private Map<?, ?> exchangeAuthorizationCode(GoogleCompleteRequest request, String redirectUri) {
        try {
            Map<?, ?> token = provider.exchangeCode(request.getCode(), request.getCodeVerifier(), redirectUri);
            if (!hasRequiredScope(token.get(SCOPE))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "GOOGLE_SCOPE_DENIED");
            }
            return token;
        } catch (GoogleCredentialException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GOOGLE_CALLBACK_REJECTED");
        }
    }

    private String callbackEmail(Map<?, ?> token) {
        try {
            return provider.profileEmail((String) token.get(ACCESS_TOKEN));
        } catch (GoogleCredentialException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GOOGLE_CALLBACK_REJECTED");
        }
    }

    private void requireAccountOwnership(String email, User user) {
        accounts.findByGoogleUserId(email)
                .filter(account -> !account.getUser().getId().equals(user.getId()))
                .ifPresent(account -> {
                    throw new ApiException(HttpStatus.FORBIDDEN, "GOOGLE_ACCOUNT_UNAVAILABLE");
                });
    }

    private String resolveRefreshToken(Map<?, ?> token, GoogleAccount existing, String email) {
        String refreshToken = refreshToken(token);
        if (refreshToken != null) {
            return refreshToken;
        }
        if (existing == null || !email.equals(existing.getEmail()) || existing.getRefreshToken() == null) {
            throw refreshRequired();
        }
        return verifyExistingRefreshToken(existing, email);
    }

    private String verifyExistingRefreshToken(GoogleAccount existing, String email) {
        try {
            Map<?, ?> verified = provider.refresh(existing.getRefreshToken());
            if (hasLostScope(verified)
                    || !email.equals(provider.profileEmail((String) verified.get(ACCESS_TOKEN)))) {
                throw refreshRequired();
            }
            String rotated = refreshToken(verified);
            return rotated == null ? existing.getRefreshToken() : rotated;
        } catch (GoogleCredentialException ex) {
            throw refreshRequired();
        }
    }

    private void revokeReplacedAccount(GoogleAccount existing, String email) {
        if (existing != null && !email.equals(existing.getEmail())) {
            provider.revoke(revocationToken(existing));
            cachedEmails.deleteCachedEmails(existing.getId());
        }
    }

    private GoogleAccount saveConnection(GoogleAccount existing, User user, String email,
                                         Map<?, ?> token, String refreshToken) {
        GoogleAccount account = existing == null ? new GoogleAccount() : existing;
        account.setUser(user);
        account.setGoogleUserId(email);
        account.setEmail(email);
        mapper.updateToken(token, account);
        account.setRefreshToken(refreshToken);
        account.setGrantedScope(GoogleSettings.SCOPE);
        account.setReconnectRequired(false);
        account.setRevocationPending(false);
        accounts.saveAndFlush(account);
        return account;
    }

    private void checkConnectionHealth(GoogleAccount account) {
        if (requiresReconnect(account)) {
            return;
        }
        if (!GoogleSettings.SCOPE.equals(account.getGrantedScope())) {
            markPermissionLost(account);
            return;
        }
        try {
            if (!refreshAccessTokenIfNeeded(account)) {
                return;
            }
            if (!account.getEmail().equals(provider.profileEmail(account.getAccessToken().getTokenValue()))) {
                markPermissionLost(account);
            }
        } catch (GoogleCredentialException ex) {
            account.setReconnectRequired(true);
        }
    }

    private boolean refreshAccessTokenIfNeeded(GoogleAccount account) {
        if (account.getAccessTokenExpiresAt().isAfter(Instant.now().plus(TOKEN_REFRESH_MARGIN))) {
            return true;
        }
        if (account.getRefreshToken() == null) {
            account.setReconnectRequired(true);
            return false;
        }
        Map<?, ?> token = provider.refresh(account.getRefreshToken());
        if (hasLostScope(token)) {
            markPermissionLost(account);
            return false;
        }
        mapper.updateToken(token, account);
        String rotated = refreshToken(token);
        if (rotated != null) {
            account.setRefreshToken(rotated);
        }
        return true;
    }

    private void markPermissionLost(GoogleAccount account) {
        account.setReconnectRequired(true);
        account.setGrantedScope("");
    }

    private void revokeAndRemove(GoogleAccount account) {
        try {
            provider.revoke(revocationToken(account));
        } catch (ApiException ex) {
            account.setRevocationRetryAt(Instant.now().plus(REVOCATION_RETRY_DELAY));
            throw ex;
        }
        accounts.delete(account);
        accounts.flush();
    }

    private static boolean requiresReconnect(GoogleAccount account) {
        return account.isReconnectRequired() || account.isRevocationPending();
    }

    private static String revocationToken(GoogleAccount account) {
        return account.getRefreshToken() == null
                ? account.getAccessToken().getTokenValue() : account.getRefreshToken();
    }

    private static String refreshToken(Map<?, ?> token) {
        return token.get(REFRESH_TOKEN) instanceof String value && !value.isBlank() ? value : null;
    }

    private static boolean hasLostScope(Map<?, ?> token) {
        return token.containsKey(SCOPE) && !hasRequiredScope(token.get(SCOPE));
    }

    private static boolean hasRequiredScope(Object scope) {
        return scope instanceof String value && Arrays.asList(value.split(" ")).contains(GoogleSettings.SCOPE);
    }

    private static ApiException refreshRequired() {
        return new ApiException(HttpStatus.BAD_REQUEST, "GOOGLE_REFRESH_REQUIRED");
    }
}
