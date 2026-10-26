package com.mymail.integration.google.service;

import com.mymail.common.ApiException;
import com.mymail.integration.google.mappers.OAuthStateMapper;
import com.mymail.integration.google.repository.GoogleOAuthStateRepository;
import com.mymail.user.entities.GoogleOAuthState;
import com.mymail.user.entities.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.mymail.integration.google.GoogleOAuthConstants.PKCE_METHOD;
import static com.mymail.integration.google.GoogleOAuthConstants.TRANSACTION_LIFETIME_SECONDS;

@Service
@RequiredArgsConstructor
public class OAuthStateService {
    private static final int STATE_RANDOM_BYTES = 32;
    private static final String PENDING_STATUS = "pending";
    private static final String PKCE_HASH_ALGORITHM = "SHA-256";
    private static final int SUCCESSFUL_CLAIM_COUNT = 1;

    private final GoogleOAuthStateRepository repository;
    private final OAuthStateMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public GoogleOAuthState create(User user, String redirect, String challenge) {
        GoogleOAuthState state = mapper.toEntity(generateState(), user, redirect, challenge,
                Instant.now().plusSeconds(TRANSACTION_LIFETIME_SECONDS));
        return repository.saveAndFlush(state);
    }

    // Commit the claim independently: provider failures must never make a transaction reusable.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GoogleOAuthState validateAndConsume(String state, String verifier, String redirect, User authenticatedUser) {
        GoogleOAuthState transaction = repository.findByState(state).orElseThrow(OAuthStateService::invalid);
        requireOwnership(transaction, authenticatedUser);
        requireFreshTransaction(transaction, redirect);
        requireMatchingChallenge(transaction, verifier);
        claimTransaction(transaction);
        return transaction;
    }

    public static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance(PKCE_HASH_ALGORITHM)
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private String generateState() {
        byte[] bytes = new byte[STATE_RANDOM_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void requireOwnership(GoogleOAuthState transaction, User user) {
        if (!transaction.getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OAUTH_OWNER_MISMATCH");
        }
    }

    private void requireFreshTransaction(GoogleOAuthState transaction, String redirect) {
        if (!transaction.getExpiresAt().isAfter(Instant.now())
                || !PENDING_STATUS.equals(transaction.getTransactionStatus())
                || !redirect.equals(transaction.getRedirectUri())
                || !PKCE_METHOD.equals(transaction.getCodeChallengeMethod())) {
            throw invalid();
        }
    }

    private void requireMatchingChallenge(GoogleOAuthState transaction, String verifier) {
        byte[] supplied = challenge(verifier).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = transaction.getCodeChallenge().getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(supplied, expected)) {
            throw invalid();
        }
    }

    private void claimTransaction(GoogleOAuthState transaction) {
        if (repository.claim(transaction.getId(), Instant.now()) != SUCCESSFUL_CLAIM_COUNT) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OAUTH_TRANSACTION");
    }
}
