package com.mymail.user.service;

import com.mymail.authentication.service.IdentityPolicy;
import com.mymail.common.ApiException;
import com.mymail.user.entities.User;
import com.mymail.user.mappers.UserMapper;
import com.mymail.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public void requireEmailAvailable(String email) {
        if (userRepository.existsByEmailIgnoreCase(email))
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS");
    }

    public User saveRegisteredUser(String idpId, String email) {
        return userRepository.findByIdpId(idpId).orElseGet(() -> {
            User user = userMapper.toEntity(idpId, IdentityPolicy.email(email));
            return userRepository.save(user);
        });
    }

    public User getOrCreateUser(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken) || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("A validated Auth0 identity is required");
        }
        String idpId = authentication.getName();

        User user = userRepository.findByIdpId(idpId).orElseGet(() -> createUser(authentication));
        if (user.getIdpId() == null || user.getIdpId().isBlank())
            throw new AccessDeniedException("Invalid profile");
        try { IdentityPolicy.email(user.getEmail()); }
        catch (RuntimeException ex) { throw new AccessDeniedException("Invalid profile"); }
        return user;
    }

    private User createUser(Authentication authentication) {
        String idpId = authentication.getName();
        String email = getEmail(authentication);

        User user = userMapper.toEntity(idpId, IdentityPolicy.email(email));

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

        if (!(email instanceof String) || email.toString().isBlank()) {
            throw new AccessDeniedException("Required email claim missing");
        }

        try { return IdentityPolicy.email((String) email); }
        catch (RuntimeException ex) { throw new AccessDeniedException("Invalid email claim"); }
    }
}
