package com.mymail.authentication.service;

import com.mymail.api.model.LoginRequest;
import com.mymail.api.model.LoginResponse;
import com.mymail.api.model.SignInRequest;
import com.mymail.authentication.mappers.AuthenticationMapper;
import com.mymail.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationServiceTest {
    private final Auth0Client auth0 = mock(Auth0Client.class);
    private final UserService users = mock(UserService.class);
    private final AuthenticationService service = new AuthenticationService(auth0, Mappers.getMapper(AuthenticationMapper.class), users, mock(AuthenticationRateLimiter.class), mock(TokenRevocations.class));

    @Test
    void registersPersistsIdentityAndReturnsTokenInOrder() {
        when(auth0.register("user@example.com", "password123")).thenReturn("auth0|123");
        when(auth0.login("user@example.com", "password123"))
                .thenReturn(new LoginResponse().accessToken("access-token").tokenType("Bearer"));

        var result = service.register(new SignInRequest().email("user@example.com").password("password123"));

        assertEquals("auth0|123", result.getIdpId());
        assertEquals("access-token", result.getAccessToken());
        assertEquals("Bearer", result.getTokenType());
        var order = inOrder(auth0, users);
        order.verify(auth0).register("user@example.com", "password123");
        order.verify(users).saveRegisteredUser("auth0|123", "user@example.com");
        order.verify(auth0).login("user@example.com", "password123");
    }

    @Test
    void rejectedRegistrationDoesNotPersistOrLogin() {
        when(auth0.register(anyString(), anyString())).thenThrow(new IllegalStateException("rejected"));
        assertThrows(IllegalStateException.class,
                () -> service.register(new SignInRequest().email("user@example.com").password("password123")));
        verify(users).requireEmailAvailable("user@example.com");
        verify(users, never()).saveRegisteredUser(anyString(), anyString());
        verify(auth0, never()).login(anyString(), anyString());
    }

    @Test
    void loginReturnsProviderToken() {
        var token = new LoginResponse().accessToken("access-token").tokenType("Bearer");
        when(auth0.login("user@example.com", "password123")).thenReturn(token);
        assertSame(token, service.login(new LoginRequest().email("user@example.com").password("password123")));
        verifyNoInteractions(users);
    }
}
