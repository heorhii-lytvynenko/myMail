package com.mymail.authentication.service;

import com.mymail.api.model.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class Auth0Client {
    private final RestClient client;
    private final String clientId;
    private final String clientSecret;
    private final String audience;
    private final String connection;

    public Auth0Client(RestClient.Builder builder,
                       @Value("${auth0.domain}") String domain,
                       @Value("${auth0.client-id}") String clientId,
                       @Value("${auth0.client-secret}") String clientSecret,
                       @Value("${auth0.audience}") String audience,
                       @Value("${auth0.connection}") String connection) {
        this.client = builder.baseUrl("https://" + domain).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.audience = audience;
        this.connection = connection;
    }

    public LoginResponse login(String email, String password) {
        Map<?, ?> response = post("/oauth/token", Map.of(
                "grant_type", "http://auth0.com/oauth/grant-type/password-realm",
                "client_id", clientId, "client_secret", clientSecret,
                "audience", audience, "realm", connection,
                "username", email, "password", password, "scope", "openid email"), false);
        if (response == null || !(response.get("access_token") instanceof String token) || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from identity provider");
        }
        return new LoginResponse().accessToken(token).tokenType("Bearer");
    }

    public String register(String email, String password) {
        Map<?, ?> response = post("/dbconnections/signup", Map.of("client_id", clientId,
                "connection", connection, "email", email, "password", password), true);
        if (response == null || !(response.get("_id") instanceof String id) || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid registration response from identity provider");
        }
        return id.startsWith("auth0|") ? id : "auth0|" + id;
    }

    private Map<?, ?> post(String path, Map<String, String> body, boolean signup) {
        try {
            return client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(Map.class);
        } catch (RestClientResponseException ex) {
            // Never expose Auth0 response bodies, credentials, or client configuration.
            int status = ex.getStatusCode().value();
            if (status == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many authentication attempts");
            }
            if (signup && (status == 409 || (status == 400 && isDuplicateUser(ex)))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists");
            }
            if (signup && status == 400) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration rejected by identity provider");
            }
            if (!signup && (status == 400 || status == 403)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication rejected by identity provider");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider request failed");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity provider unavailable");
        }
    }

    private boolean isDuplicateUser(RestClientResponseException ex) {
        try {
            Map<?, ?> error = ex.getResponseBodyAs(Map.class);
            return error != null && ("user_exists".equals(error.get("code"))
                    || "user_exists".equals(error.get("error")));
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
