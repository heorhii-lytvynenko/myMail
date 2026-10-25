package com.mymail.integration.google.service;

import com.mymail.authentication.service.IdentityPolicy;
import com.mymail.common.ApiException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import static com.mymail.integration.google.GoogleOAuthConstants.*;

@Service
public class GoogleOAuthService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final String INVALID_GRANT = "invalid_grant";
    private static final String INVALID_TOKEN = "invalid_token";
    private static final String ERROR = "error";
    private static final String INSUFFICIENT_PERMISSIONS = "insufficientPermissions";

    private final RestClient client;
    private final String clientId;
    private final String clientSecret;

    @Autowired
    public GoogleOAuthService(RestClient.Builder builder,
                              @Value("${google.oauth.client-id:}") String clientId,
                              @Value("${google.oauth.client-secret:}") String clientSecret,
                              @Value("${google.oauth.enabled:false}") boolean enabled) {
        requireClientConfiguration(enabled, clientId, clientSecret);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.client = createClient(builder);
    }

    GoogleOAuthService(RestClient client, String clientId, String clientSecret) {
        this.client = client;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String createAuthorizationUrl(String state, String redirect, String challenge) {
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirect)
                .queryParam("response_type", "code")
                .queryParam(SCOPE, GoogleSettings.SCOPE)
                .queryParam("state", state)
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", PKCE_METHOD)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .build().encode().toUriString();
    }

    public Map<?, ?> exchangeCode(String code, String verifier, String redirect) {
        MultiValueMap<String, String> request = form(
                "grant_type", "authorization_code",
                "code", code,
                "code_verifier", verifier,
                "redirect_uri", redirect);
        return requestToken(request);
    }

    public Map<?, ?> refresh(String refreshToken) {
        return requestToken(form("grant_type", REFRESH_TOKEN, REFRESH_TOKEN, refreshToken));
    }

    public String profileEmail(String accessToken) {
        try {
            RestClient.RequestHeadersSpec<?> request = client.get().uri(PROFILE_URL);
            request.headers(headers -> headers.setBearerAuth(accessToken));
            Map<?, ?> profile = request.retrieve().body(Map.class);
            return normalizedProfileEmail(profile);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value() || isPermissionLoss(ex)) {
                throw new GoogleCredentialException();
            }
            throw unavailable();
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    public void revoke(String token) {
        try {
            client.post()
                    .uri(REVOCATION_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form("token", token))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // Already revoked credentials are an idempotent success; other failures remain pending.
            if (isBadRequest(ex) && INVALID_TOKEN.equals(errorCode(ex))) {
                return;
            }
            throw unavailable();
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    private Map<?, ?> requestToken(MultiValueMap<String, String> request) {
        request.add("client_id", clientId);
        request.add("client_secret", clientSecret);
        try {
            Map<?, ?> response = client.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            requireValidTokenResponse(response);
            return response;
        } catch (RestClientResponseException ex) {
            if (isRejectedCredential(ex)) {
                throw new GoogleCredentialException();
            }
            throw unavailable();
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    private static void requireValidTokenResponse(Map<?, ?> response) {
        if (response == null || !(response.get(ACCESS_TOKEN) instanceof String token) || token.isBlank()
                || !(response.get(EXPIRES_IN) instanceof Number lifetime) || lifetime.longValue() <= 0
                || !BEARER.equalsIgnoreCase(String.valueOf(response.get(TOKEN_TYPE)))) {
            throw unavailable();
        }
    }

    private static String normalizedProfileEmail(Map<?, ?> profile) {
        if (profile == null || !(profile.get("emailAddress") instanceof String email)) {
            throw unavailable();
        }
        try {
            return IdentityPolicy.email(email);
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    private static RestClient createClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return builder.requestFactory(factory).build();
    }

    private static void requireClientConfiguration(boolean enabled, String clientId, String clientSecret) {
        if (enabled && (clientId.isBlank() || clientSecret.isBlank())) {
            throw new IllegalStateException("Google OAuth client configuration is required when enabled");
        }
    }

    private static boolean isBadRequest(RestClientResponseException ex) {
        return ex.getStatusCode().value() == HttpStatus.BAD_REQUEST.value();
    }

    private static boolean isRejectedCredential(RestClientResponseException ex) {
        String error = errorCode(ex);
        return isBadRequest(ex) && (INVALID_GRANT.equals(error) || INVALID_TOKEN.equals(error));
    }

    private static String errorCode(RestClientResponseException ex) {
        try {
            Map<?, ?> body = ex.getResponseBodyAs(Map.class);
            return body != null && body.get(ERROR) instanceof String error ? error : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean isPermissionLoss(RestClientResponseException ex) {
        if (ex.getStatusCode().value() != HttpStatus.FORBIDDEN.value()) {
            return false;
        }
        try {
            Map<?, ?> body = ex.getResponseBodyAs(Map.class);
            if (body == null || !(body.get(ERROR) instanceof Map<?, ?> error)
                    || !(error.get("errors") instanceof List<?> errors)) {
                return false;
            }
            return errors.stream().anyMatch(GoogleOAuthService::isInsufficientPermission);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isInsufficientPermission(Object value) {
        return value instanceof Map<?, ?> detail && INSUFFICIENT_PERMISSIONS.equals(detail.get("reason"));
    }

    private static MultiValueMap<String, String> form(String... pairs) {
        MultiValueMap<String, String> result = new LinkedMultiValueMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.add(pairs[index], pairs[index + 1]);
        }
        return result;
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "GOOGLE_PROVIDER_FAILURE");
    }
}
