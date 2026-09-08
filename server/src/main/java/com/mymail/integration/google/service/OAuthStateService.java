package com.mymail.integration.google.service;


import com.mymail.integration.google.repository.GoogleOAuthStateRepository;
import com.mymail.user.entities.GoogleOAuthState;
import com.mymail.user.entities.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class OAuthStateService {

    private static final int STATE_EXPIRATION_MINUTES = 10;

    private final GoogleOAuthStateRepository repository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String create(User user) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        String state = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        GoogleOAuthState oauthState = new GoogleOAuthState();
        oauthState.setState(state);
        oauthState.setUser(user);
        oauthState.setExpiresAt(
                Instant.now().plusSeconds(STATE_EXPIRATION_MINUTES * 60L)
        );

        repository.save(oauthState);

        return state;
    }

    @Transactional
    public User consumeUser(String state) {
        GoogleOAuthState oauthState = repository.findByState(state)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid OAuth state")
                );

        if (oauthState.getExpiresAt().isBefore(Instant.now())) {
            repository.delete(oauthState);
            throw new IllegalArgumentException("Expired OAuth state");
        }

        User user = oauthState.getUser();

        repository.delete(oauthState);

        return user;
    }
}