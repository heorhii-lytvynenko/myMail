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

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new Auth0Client(builder, "tenant.auth0.com", "client-id", "secret", "api", "database");
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
}
