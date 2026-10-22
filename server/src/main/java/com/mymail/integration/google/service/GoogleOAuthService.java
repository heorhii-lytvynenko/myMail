package com.mymail.integration.google.service;

import com.mymail.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.*;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

@Service
public class GoogleOAuthService {
    private final RestClient client;
    private final String clientId;
    private final String clientSecret;
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleOAuthService(RestClient.Builder builder,
                              @Value("${google.oauth.client-id:}") String clientId,
                              @Value("${google.oauth.client-secret:}") String clientSecret,
                              @Value("${google.oauth.enabled:false}") boolean enabled) {
        if (enabled && (clientId.isBlank() || clientSecret.isBlank()))
            throw new IllegalStateException("Google OAuth client configuration is required when enabled");
        this.clientId = clientId; this.clientSecret = clientSecret;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = builder.requestFactory(factory).build();
    }
    GoogleOAuthService(RestClient client, String clientId, String clientSecret) {
        this.client = client; this.clientId = clientId; this.clientSecret = clientSecret;
    }
    public String createAuthorizationUrl(String state, String redirect, String challenge) {
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId).queryParam("redirect_uri", redirect)
                .queryParam("response_type", "code").queryParam("scope", GoogleSettings.SCOPE)
                .queryParam("state", state).queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256").queryParam("access_type", "offline")
                .queryParam("prompt", "consent").build().encode().toUriString();
    }
    public Map<?, ?> exchangeCode(String code, String verifier, String redirect) {
        return token(form("grant_type", "authorization_code", "code", code,
                "code_verifier", verifier, "redirect_uri", redirect));
    }
    public Map<?, ?> refresh(String refresh) {
        return token(form("grant_type", "refresh_token", "refresh_token", refresh));
    }
    private Map<?, ?> token(MultiValueMap<String, String> form) {
        form.add("client_id", clientId); form.add("client_secret", clientSecret);
        try {
            Map<?, ?> result = client.post().uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            if (result == null || !(result.get("access_token") instanceof String token) || token.isBlank()
                    || !(result.get("expires_in") instanceof Number lifetime) || lifetime.longValue() <= 0
                    || !"Bearer".equalsIgnoreCase(String.valueOf(result.get("token_type")))) throw unavailable();
            return result;
        } catch (RestClientResponseException ex) {
            String error = errorCode(ex);
            if (ex.getStatusCode().value() == 400 && ("invalid_grant".equals(error) || "invalid_token".equals(error)))
                throw new GoogleCredentialException();
            throw unavailable();
        } catch (RestClientException ex) { throw unavailable(); }
    }
    public String profileEmail(String accessToken) {
        try {
            Map<?, ?> profile = client.get().uri("https://gmail.googleapis.com/gmail/v1/users/me/profile")
                    .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(Map.class);
            if (profile == null || !(profile.get("emailAddress") instanceof String email)) throw unavailable();
            try { return com.mymail.authentication.service.IdentityPolicy.email(email); }
            catch (RuntimeException ex) { throw unavailable(); }
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401 || isPermissionLoss(ex)) throw new GoogleCredentialException();
            throw unavailable();
        } catch (RestClientException ex) { throw unavailable(); }
    }
    public void revoke(String token) {
        try {
            client.post().uri("https://oauth2.googleapis.com/revoke")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form("token", token))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // An already revoked token is an idempotent success. Other failures remain pending.
            if (ex.getStatusCode().value() == 400 && "invalid_token".equals(errorCode(ex))) return;
            throw unavailable();
        } catch (RestClientException ex) { throw unavailable(); }
    }
    private static String errorCode(RestClientResponseException ex) {
        try {
            Map<?, ?> body = ex.getResponseBodyAs(Map.class);
            return body != null && body.get("error") instanceof String error ? error : "";
        } catch (RuntimeException ignored) { return ""; }
    }
    private static boolean isPermissionLoss(RestClientResponseException ex) {
        if (ex.getStatusCode().value() != 403) return false;
        try {
            Map<?, ?> body = ex.getResponseBodyAs(Map.class);
            if (body != null && body.get("error") instanceof Map<?, ?> error
                    && error.get("errors") instanceof List<?> errors)
                return errors.stream().anyMatch(value -> value instanceof Map<?, ?> detail
                        && "insufficientPermissions".equals(detail.get("reason")));
        } catch (RuntimeException ignored) { }
        return false;
    }
    private static MultiValueMap<String, String> form(String... pairs) {
        MultiValueMap<String, String> result = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.add(pairs[i], pairs[i + 1]);
        return result;
    }
    private static ApiException unavailable() { return new ApiException(HttpStatus.BAD_GATEWAY, "GOOGLE_PROVIDER_FAILURE"); }
}
