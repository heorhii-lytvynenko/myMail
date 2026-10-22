package com.mymail.authentication.service;

import com.mymail.api.model.LoginResponse;
import com.mymail.common.ApiException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;


import java.util.Map;

@Component
public class Auth0Client {
    private final RestClient client;
    private final JwtDecoder decoder;
    private final String clientId;
    private final String clientSecret;
    private final String audience;
    private final String connection;

    @org.springframework.beans.factory.annotation.Autowired
    public Auth0Client(RestClient.Builder builder,
                       @Value("${auth0.domain}") String domain,
                       @Value("${auth0.client-id}") String clientId,
                       @Value("${auth0.client-secret}") String clientSecret,
                       @Value("${auth0.audience}") String audience,
                       @Value("${auth0.connection}") String connection, JwtDecoder decoder) {
        this(secureClient(builder, domain), clientId, clientSecret, audience, connection, decoder);
    }

    Auth0Client(RestClient client, String clientId, String clientSecret, String audience, String connection, JwtDecoder decoder) {
        this.decoder = decoder;
        this.client = client;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.audience = audience;
        this.connection = connection;
    }

    private static RestClient secureClient(RestClient.Builder builder, String domain) {
        if (domain.contains("/") || domain.contains("@") || domain.contains(":") || domain.isBlank())
            throw new IllegalStateException("Invalid identity provider hostname");
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        return builder.requestFactory(factory).baseUrl("https://" + domain).build();
    }

    public LoginResponse login(String email, String password) {
        Instant started = Instant.now();
        Map<?, ?> response = post("/oauth/token", Map.of(
                "grant_type", "http://auth0.com/oauth/grant-type/password-realm",
                "client_id", clientId, "client_secret", clientSecret,
                "audience", audience, "realm", connection,
                "username", email, "password", password, "scope", "openid email"), false);
        if (response == null || !(response.get("access_token") instanceof String token) || token.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_IDENTITY_RESPONSE");
        }
        try {
            if (response.containsKey("token_type") && !"Bearer".equalsIgnoreCase(String.valueOf(response.get("token_type"))))
                throw new IllegalArgumentException();
            if (response.containsKey("expires_in") && !(response.get("expires_in") instanceof Number))
                throw new IllegalArgumentException();
            var jwt = decoder.decode(token);
            long remaining = java.time.Duration.between(Instant.now(), jwt.getExpiresAt()).getSeconds();
            if (response.get("expires_in") instanceof Number duration) {
                long providerRemaining = duration.longValue() - java.time.Duration.between(started, Instant.now()).getSeconds();
                remaining = Math.min(remaining, providerRemaining);
            }
            if (remaining <= 0) throw new IllegalArgumentException();
            return new LoginResponse().accessToken(token).tokenType("Bearer").expiresIn(remaining);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_IDENTITY_RESPONSE");
        }
    }

    public String register(String email, String password) {
        Map<?, ?> response = post("/dbconnections/signup", Map.of("client_id", clientId,
                "connection", connection, "email", email, "password", password), true);
        if (response == null || !(response.get("_id") instanceof String id) || id.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_IDENTITY_RESPONSE");
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
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "IDENTITY_RATE_LIMITED");
            }
            if (signup && (status == 409 || (status == 400 && isDuplicateUser(ex)))) {
                throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS");
            }
            if (signup && status == 400) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "REGISTRATION_REJECTED");
            }
            String providerError = errorCode(ex);
            if (java.util.Set.of("invalid_client", "unauthorized_client", "unsupported_grant_type", "invalid_request")
                    .contains(providerError)) throw new ApiException(HttpStatus.BAD_GATEWAY, "IDENTITY_PROVIDER_FAILURE");
            if (!signup && (status == 400 || status == 401 || status == 403)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "IDENTITY_PROVIDER_FAILURE");
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_PROVIDER_UNAVAILABLE");
        }
    }

    private static String errorCode(RestClientResponseException ex) {
        try {
            Map<?, ?> error = ex.getResponseBodyAs(Map.class);
            return error != null && error.get("error") instanceof String value ? value : "";
        } catch (RuntimeException ignored) { return ""; }
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
