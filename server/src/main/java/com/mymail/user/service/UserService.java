package com.mymail.user.service;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import com.mymail.user.entities.User;
import com.mymail.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User saveRegisteredUser(String idpId, String email) {
        return userRepository.findByIdpId(idpId).orElseGet(() -> {
            User user = new User();
            user.setIdpId(idpId);
            user.setEmail(email);
            return userRepository.save(user);
        });
    }

    public User getOrCreateUser(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken) || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("A validated Auth0 identity is required");
        }
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
        if (!(authentication instanceof JwtAuthenticationToken auth0Authentication)) {
            throw new IllegalStateException(
                    "Expected Auth0 authentication but got: "
                            + authentication.getClass().getName()
            );
        }

        Object email = auth0Authentication.getToken().getClaim("https://mymail.com/email");

        if (email == null || email.toString().isBlank()) {
            throw new IllegalStateException("Email claim is missing from Auth0 token");
        }

        return email.toString();
    }
}
