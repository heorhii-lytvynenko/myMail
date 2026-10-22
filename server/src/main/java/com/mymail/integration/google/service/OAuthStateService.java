package com.mymail.integration.google.service;

import com.mymail.common.ApiException;
import com.mymail.integration.google.repository.GoogleOAuthStateRepository;
import com.mymail.user.entities.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class OAuthStateService {
    private final GoogleOAuthStateRepository repository;
    private final SecureRandom random = new SecureRandom();
    public GoogleOAuthState create(User user, String redirect, String challenge) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        GoogleOAuthState state = new GoogleOAuthState();
        state.setState(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        state.setUser(user); state.setRedirectUri(redirect); state.setCodeChallenge(challenge);
        state.setCodeChallengeMethod("S256"); state.setExpiresAt(Instant.now().plusSeconds(600));
        return repository.saveAndFlush(state);
    }
    // Commit the claim independently: provider failures must never make a transaction reusable.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GoogleOAuthState validateAndConsume(String state, String verifier, String redirect, User authenticatedUser) {
        GoogleOAuthState transaction = repository.findByState(state).orElseThrow(OAuthStateService::invalid);
        if (!transaction.getUser().getId().equals(authenticatedUser.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "OAUTH_OWNER_MISMATCH");
        if (!transaction.getExpiresAt().isAfter(Instant.now()) || !"pending".equals(transaction.getTransactionStatus())
                || !redirect.equals(transaction.getRedirectUri()) || !"S256".equals(transaction.getCodeChallengeMethod())
                || !MessageDigest.isEqual(challenge(verifier).getBytes(StandardCharsets.US_ASCII),
                    transaction.getCodeChallenge().getBytes(StandardCharsets.US_ASCII))) throw invalid();
        if (repository.claim(transaction.getId(), Instant.now()) != 1) throw invalid();
        return transaction;
    }
    public static String challenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OAUTH_TRANSACTION"); }
}
