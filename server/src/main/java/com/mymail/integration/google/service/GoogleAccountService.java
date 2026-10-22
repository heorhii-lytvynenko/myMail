package com.mymail.integration.google.service;

import com.mymail.api.model.*;
import com.mymail.authentication.service.AuthenticationRateLimiter;
import com.mymail.common.ApiException;
import com.mymail.integration.google.entities.GoogleAccount;
import com.mymail.integration.google.repository.*;
import com.mymail.user.entities.User;
import com.mymail.user.repository.UserRepository;
import com.mymail.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GoogleAccountService {
    private final GoogleAccountRepository accounts;
    private final GoogleOAuthStateRepository states;
    private final OAuthStateService transactions;
    private final GoogleOAuthService provider;
    private final UserService users;
    private final UserRepository userRepository;
    private final GoogleSettings settings;
    private final AuthenticationRateLimiter limiter;

    private User lock(Authentication authentication) {
        User user = users.getOrCreateUser(authentication);
        return userRepository.lockById(user.getId()).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "USER_UNAVAILABLE"));
    }
    @Transactional
    public GoogleAuthorizationResponse authorize(Authentication authentication, GoogleAuthorizeRequest request) {
        settings.requireEnabled(); settings.checkRedirect(request.getRedirectUri());
        User user = lock(authentication);
        limiter.check("google-authorize", user.getIdpId(), 10);
        accounts.findByUserId(user.getId()).filter(GoogleAccount::isRevocationPending)
                .ifPresent(account -> { throw new ApiException(HttpStatus.CONFLICT, "DISCONNECT_PENDING"); });
        var state = transactions.create(user, request.getRedirectUri(), request.getCodeChallenge());
        return new GoogleAuthorizationResponse().state(state.getState()).expiresIn(600)
                .authorizationUrl(java.net.URI.create(provider.createAuthorizationUrl(state.getState(), request.getRedirectUri(), request.getCodeChallenge())));
    }
    @Transactional
    public GoogleConnectionResponse complete(Authentication authentication, GoogleCompleteRequest request) {
        settings.requireEnabled(); settings.checkRedirect(request.getRedirectUri());
        User user = lock(authentication);
        GoogleAccount existing = accounts.findByUserId(user.getId()).orElse(null);
        if (existing != null && existing.isRevocationPending()) throw new ApiException(HttpStatus.CONFLICT, "DISCONNECT_PENDING");
        var transaction = transactions.validateAndConsume(request.getState(), request.getCodeVerifier(), request.getRedirectUri(), user);
        Map<?, ?> token;
        String email;
        try {
            token = provider.exchangeCode(request.getCode(), request.getCodeVerifier(), transaction.getRedirectUri());
            if (!hasScope(token.get("scope"))) throw new ApiException(HttpStatus.BAD_REQUEST, "GOOGLE_SCOPE_DENIED");
            email = provider.profileEmail((String) token.get("access_token"));
        } catch (GoogleCredentialException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "GOOGLE_CALLBACK_REJECTED"); }
        accounts.findByGoogleUserId(email).filter(other -> !other.getUser().getId().equals(user.getId()))
                .ifPresent(other -> { throw new ApiException(HttpStatus.FORBIDDEN, "GOOGLE_ACCOUNT_UNAVAILABLE"); });
        String refresh = token.get("refresh_token") instanceof String value && !value.isBlank() ? value : null;
        if (refresh == null && existing != null && email.equals(existing.getEmail()) && existing.getRefreshToken() != null) {
            try {
                Map<?, ?> verified = provider.refresh(existing.getRefreshToken());
                if ((verified.containsKey("scope") && !hasScope(verified.get("scope")))
                        || !email.equals(provider.profileEmail((String) verified.get("access_token"))))
                    throw new ApiException(HttpStatus.BAD_REQUEST, "GOOGLE_REFRESH_REQUIRED");
                refresh = verified.get("refresh_token") instanceof String rotated && !rotated.isBlank()
                        ? rotated : existing.getRefreshToken();
            } catch (GoogleCredentialException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "GOOGLE_REFRESH_REQUIRED"); }
        }
        if (refresh == null) throw new ApiException(HttpStatus.BAD_REQUEST, "GOOGLE_REFRESH_REQUIRED");
        if (existing != null && !email.equals(existing.getEmail()))
            provider.revoke(existing.getRefreshToken() == null ? existing.getAccessToken() : existing.getRefreshToken());
        GoogleAccount account = existing == null ? new GoogleAccount() : existing;
        account.setUser(user); account.setGoogleUserId(email); account.setEmail(email);
        applyToken(account, token); account.setRefreshToken(refresh);
        account.setGrantedScope(GoogleSettings.SCOPE); account.setReconnectRequired(false); account.setRevocationPending(false);
        accounts.saveAndFlush(account);
        return response(account);
    }
    @Transactional(noRollbackFor = ApiException.class)
    public GoogleConnectionResponse status(Authentication authentication) {
        settings.requireEnabled();
        User user = lock(authentication);
        GoogleAccount account = accounts.findByUserId(user.getId()).orElse(null);
        if (account == null) return new GoogleConnectionResponse().status(GoogleConnectionResponse.StatusEnum.DISCONNECTED).scopes(null);
        healthy(account);
        return response(account);
    }
    private void healthy(GoogleAccount account) {
        if (account.isReconnectRequired() || account.isRevocationPending()) return;
        if (!GoogleSettings.SCOPE.equals(account.getGrantedScope())) {
            account.setReconnectRequired(true); account.setGrantedScope(""); return;
        }
        try {
            if (!account.getAccessTokenExpiresAt().isAfter(Instant.now().plusSeconds(30))) {
                if (account.getRefreshToken() == null) { account.setReconnectRequired(true); return; }
                Map<?, ?> token = provider.refresh(account.getRefreshToken());
                if (token.containsKey("scope") && !hasScope(token.get("scope"))) {
                    account.setReconnectRequired(true); account.setGrantedScope(""); return;
                }
                applyToken(account, token);
                if (token.get("refresh_token") instanceof String refresh && !refresh.isBlank()) account.setRefreshToken(refresh);
            }
            if (!account.getEmail().equals(provider.profileEmail(account.getAccessToken()))) {
                account.setReconnectRequired(true); account.setGrantedScope("");
            }
        } catch (GoogleCredentialException ex) {
            account.setReconnectRequired(true);
        }
    }
    @Transactional(noRollbackFor = ApiException.class)
    public GoogleAccount getByAuthentication(Authentication authentication) {
        settings.requireEnabled();
        User user = lock(authentication);
        GoogleAccount account = accounts.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GOOGLE_DISCONNECTED"));
        healthy(account);
        if (account.isReconnectRequired() || account.isRevocationPending())
            throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_RECONNECT_REQUIRED");
        return account;
    }
    @Transactional(noRollbackFor = ApiException.class)
    public void disconnect(Authentication authentication) {
        User user = lock(authentication);
        states.cancel(user.getId());
        GoogleAccount account = accounts.findByUserId(user.getId()).orElse(null);
        if (account == null) return;
        account.setRevocationPending(true); account.setReconnectRequired(true);
        // Durable pending flag blocks every local credential read and completion even on outage.
        accounts.saveAndFlush(account);
        revokeAndRemove(account);
    }
    @Transactional(noRollbackFor = ApiException.class)
    public void retryDisconnect(UUID userId) {
        userRepository.lockById(userId).orElse(null);
        accounts.findByUserId(userId).filter(GoogleAccount::isRevocationPending).ifPresent(this::revokeAndRemove);
    }
    private void revokeAndRemove(GoogleAccount account) {
        try {
            provider.revoke(account.getRefreshToken() == null ? account.getAccessToken() : account.getRefreshToken());
        } catch (ApiException ex) {
            account.setRevocationRetryAt(Instant.now().plusSeconds(60));
            throw ex;
        }
        accounts.delete(account); accounts.flush();
    }
    private static void applyToken(GoogleAccount account, Map<?, ?> token) {
        account.setAccessToken((String) token.get("access_token"));
        account.setAccessTokenExpiresAt(Instant.now().plusSeconds(((Number) token.get("expires_in")).longValue()));
    }
    private static boolean hasScope(Object scope) {
        return scope instanceof String value && Arrays.asList(value.split(" ")).contains(GoogleSettings.SCOPE);
    }
    private static GoogleConnectionResponse response(GoogleAccount account) {
        return new GoogleConnectionResponse().status(account.isReconnectRequired() || account.isRevocationPending()
                        ? GoogleConnectionResponse.StatusEnum.RECONNECT_REQUIRED : GoogleConnectionResponse.StatusEnum.CONNECTED).email(account.getEmail())
                .scopes(GoogleSettings.SCOPE.equals(account.getGrantedScope()) ? List.of(GoogleSettings.SCOPE) : List.of());
    }
}
