package com.mymail.user.service;

import com.mymail.user.entities.User;
import com.mymail.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    @Test
    void savesRegisteredAuth0IdentityInDatabase() {
        var repository = mock(UserRepository.class);
        when(repository.findByIdpId("auth0|123")).thenReturn(Optional.empty());
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var user = new UserService(repository).saveRegisteredUser("auth0|123", "user@example.com");
        assertEquals("auth0|123", user.getIdpId());
        assertEquals("user@example.com", user.getEmail());
        verify(repository).save(user);
    }

    @Test
    void cannotProvisionAnonymousUser() {
        var repository = mock(UserRepository.class);
        var service = new UserService(repository);
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThrows(org.springframework.security.core.AuthenticationException.class,
                () -> service.getOrCreateUser(anonymous));
        verifyNoInteractions(repository);
    }

    @Test
    void provisionsLocalProfileWithAuth0SubjectAndEmailOnly() {
        var repository = mock(UserRepository.class);
        when(repository.findByIdpId("auth0|123")).thenReturn(Optional.empty());
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var jwt = Jwt.withTokenValue("validated-token").header("alg", "RS256")
                .subject("auth0|123").claim("https://mymail.com/email", "user@example.com").build();
        var user = new UserService(repository).getOrCreateUser(new JwtAuthenticationToken(jwt, java.util.List.of()));
        assertEquals("auth0|123", user.getIdpId());
        assertEquals("user@example.com", user.getEmail());
    }
}
