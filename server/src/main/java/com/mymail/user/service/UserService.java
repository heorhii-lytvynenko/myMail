package com.mymail.user.service;

import com.auth0.spring.boot.Auth0AuthenticationToken;
import com.mymail.user.entities.User;
import com.mymail.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getOrCreateUser(Authentication authentication) {
        String idpId = authentication.getName();

        return userRepository.findByIdpId(idpId)
                .orElseGet(() -> createUser(authentication));
    }

    private User createUser(Authentication authentication) {
        String idpId = authentication.getName();
        String email = getEmail(authentication);

        User user = new User();
        user.setIdpId(idpId);
        user.setEmail(email);

        return userRepository.save(user);
    }

    private String getEmail(Authentication authentication) {
        if (!(authentication instanceof Auth0AuthenticationToken auth0Authentication)) {
            throw new IllegalStateException(
                    "Expected Auth0 authentication but got: "
                            + authentication.getClass().getName()
            );
        }

        Object email = auth0Authentication.getClaim("https://mymail.com/email");

        if (email == null) {
            throw new IllegalStateException("Email claim is missing from Auth0 token");
        }

        return email.toString();
    }
}