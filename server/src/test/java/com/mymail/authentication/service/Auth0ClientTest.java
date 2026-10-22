package com.mymail.authentication.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class Auth0ClientTest {
    private MockRestServiceServer server;
    private Auth0Client client;
    private org.springframework.security.oauth2.jwt.JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        decoder = org.mockito.Mockito.mock(org.springframework.security.oauth2.jwt.JwtDecoder.class);
        org.mockito.Mockito.when(decoder.decode(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg", "RS256")
                        .subject("auth0|123").expiresAt(java.time.Instant.now().plusSeconds(3600)).build());
        client = new Auth0Client(builder.baseUrl("https://tenant.auth0.com").build(), "client-id", "secret", "api", "database", decoder);
    }

    @Test
    void registersAccountInAuth0Database() {
        server.expect(requestTo("https://tenant.auth0.com/dbconnections/signup"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().json("""
                        {"client_id":"client-id","connection":"database",
                         "email":"user@example.com","password":"password123"}
                        """))
                .andRespond(withSuccess("""
                        {"_id":"123"}
                        """, MediaType.APPLICATION_JSON));
        assertEquals("auth0|123", client.register("user@example.com", "password123"));
        server.verify();
    }

    @Test
    void requestsTokenForConfiguredApiAndConnection() {
        server.expect(requestTo("https://tenant.auth0.com/oauth/token"))
                .andExpect(content().json("""
                        {"client_id":"client-id","client_secret":"secret","audience":"api",
                         "realm":"database","username":"user@example.com","password":"password123",
                         "grant_type":"http://auth0.com/oauth/grant-type/password-realm","scope":"openid email"}
                        """))
                .andRespond(withSuccess("""
                        {"access_token":"signed-token"}
                        """, MediaType.APPLICATION_JSON));
        var response = client.login("user@example.com", "password123");
        assertEquals("signed-token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        server.verify();
    }

    @Test
    void rejectsInvalidCredentialsWithoutExposingProviderBody() {
        server.expect(requestTo("https://tenant.auth0.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("sensitive-provider-details"));
        var ex = assertThrows(ResponseStatusException.class, () -> client.login("user@example.com", "wrong"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertFalse(ex.getMessage().contains("sensitive-provider-details"));
    }

    @Test
    void propagatesDuplicateRegistrationAsConflict() {
        server.expect(requestTo("https://tenant.auth0.com/dbconnections/signup"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        var ex = assertThrows(ResponseStatusException.class, () -> client.register("user@example.com", "password123"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void rejectsMalformedTokenResponse() {
        server.expect(requestTo("https://tenant.auth0.com/oauth/token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        var ex = assertThrows(ResponseStatusException.class, () -> client.login("user@example.com", "password123"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void propagatesAuth0DuplicateErrorAsConflict() {
        server.expect(requestTo("https://tenant.auth0.com/dbconnections/signup"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"user_exists\"}"));
        var ex = assertThrows(ResponseStatusException.class, () -> client.register("user@example.com", "password123"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        server.verify();
    }

    @Test
    void rejectsRegistrationWithoutUserId() {
        server.expect(requestTo("https://tenant.auth0.com/dbconnections/signup"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        var ex = assertThrows(ResponseStatusException.class, () -> client.register("user@example.com", "password123"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void lifetimeIsBoundedByProviderAndVerifiedJwtRatherThanHardcoded() {
        server.expect(requestTo("https://tenant.auth0.com/oauth/token")).andRespond(withSuccess(
                "{\"access_token\":\"signed-token\",\"expires_in\":120}", MediaType.APPLICATION_JSON));
        long lifetime = client.login("user@example.com", "password123").getExpiresIn();
        assertTrue(lifetime > 0 && lifetime <= 120);
        org.mockito.Mockito.verify(decoder).decode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void jwtVerificationFailureIsSafeUpstreamFailure() {
        server.expect(requestTo("https://tenant.auth0.com/oauth/token")).andRespond(withSuccess(
                "{\"access_token\":\"invalid-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        org.mockito.Mockito.when(decoder.decode(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new org.springframework.security.oauth2.jwt.JwtException("private details"));
        var ex = assertThrows(ResponseStatusException.class, () -> client.login("user@example.com", "password123"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode()); assertNull(ex.getCause());
    }

    @Test
    void enforcesActualAuth0PasswordPolicyAndMapsThrottlingAndConfigurationFailures() {
        server.expect(requestTo("https://tenant.auth0.com/dbconnections/signup"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"invalid_password\",\"description\":\"private policy details\"}"));
        var policy = assertThrows(ResponseStatusException.class, () -> client.register("user@example.com", "password123"));
        assertEquals(HttpStatus.BAD_REQUEST, policy.getStatusCode()); assertFalse(policy.getMessage().contains("private"));
        server.verify(); server.reset();
        server.expect(requestTo("https://tenant.auth0.com/oauth/token")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> client.login("user@example.com", "password123")).getStatusCode());
        server.verify(); server.reset();
        server.expect(requestTo("https://tenant.auth0.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_client\"}"));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> client.login("user@example.com", "password123")).getStatusCode());
    }
}
