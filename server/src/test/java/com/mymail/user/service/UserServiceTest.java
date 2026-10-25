package com.mymail.user.service;

import com.mymail.user.entities.User;
import com.mymail.user.mappers.UserMapper;
import com.mymail.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    @Test
    void savesRegisteredAuth0IdentityInDatabase() {
        var repository = mock(UserRepository.class);
        when(repository.findByIdpId("auth0|123")).thenReturn(Optional.empty());
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var user = new UserService(repository, Mappers.getMapper(UserMapper.class)).saveRegisteredUser("auth0|123", "user@example.com");
        assertEquals("auth0|123", user.getIdpId());
        assertEquals("user@example.com", user.getEmail());
        verify(repository).save(user);
    }

    @Test
    void cannotProvisionAnonymousUser() {
        var repository = mock(UserRepository.class);
        var service = new UserService(repository, Mappers.getMapper(UserMapper.class));
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThrows(AuthenticationException.class,
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
        var user = new UserService(repository, Mappers.getMapper(UserMapper.class)).getOrCreateUser(new JwtAuthenticationToken(jwt, List.of()));
        assertEquals("auth0|123", user.getIdpId());
        assertEquals("user@example.com", user.getEmail());
    }
}
