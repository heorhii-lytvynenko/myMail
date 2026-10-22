package com.mymail.integration.google.service;

import com.mymail.common.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GoogleOAuthServiceTest {
    MockRestServiceServer server;
    GoogleOAuthService google;
    @BeforeEach void setup() {
        var builder = RestClient.builder(); server = MockRestServiceServer.bindTo(builder).build();
        google = new GoogleOAuthService(builder.build(), "client", "secret");
    }
    @Test void authorizationUrlHasExactlyOneOfEachSecurityParameterAndNoCredentials() {
        String redirect = "https://my-mail-front.vercel.app/api/integrations/google/callback";
        String challenge = "x".repeat(43); String state = "s".repeat(43);
        URI uri = URI.create(google.createAuthorizationUrl(state, redirect, challenge));
        assertEquals("https", uri.getScheme()); assertEquals("accounts.google.com", uri.getHost());
        assertEquals("/o/oauth2/v2/auth", uri.getPath()); assertEquals(-1, uri.getPort());
        var params = UriComponentsBuilder.fromUriString(uri.toString()).build().getQueryParams();
        var expected = Map.of("client_id", "client", "redirect_uri", redirect, "response_type", "code",
                "scope", GoogleSettings.SCOPE, "state", state, "code_challenge", challenge,
                "code_challenge_method", "S256", "access_type", "offline", "prompt", "consent");
        assertEquals(expected.size(), params.size());
        expected.forEach((key, value) -> {
            assertEquals(1, params.get(key).size());
            assertTrue(java.net.URLDecoder.decode(params.getFirst(key), java.nio.charset.StandardCharsets.UTF_8).equals(value));
        });
    }
    @Test void exchangeSendsOriginalRedirectAndVerifierOnlyInFormBody() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(new org.springframework.util.LinkedMultiValueMap<>(Map.of(
                        "grant_type", List.of("authorization_code"), "code", List.of("code"), "code_verifier", List.of("v".repeat(43)),
                        "redirect_uri", List.of("https://example.com/callback"), "client_id", List.of("client"), "client_secret", List.of("secret")))))
                .andRespond(withSuccess("{\"access_token\":\"access\",\"expires_in\":3600,\"token_type\":\"Bearer\",\"scope\":\"" + GoogleSettings.SCOPE + "\"}", MediaType.APPLICATION_JSON));
        assertTrue(google.exchangeCode("code", "v".repeat(43), "https://example.com/callback").get("access_token").equals("access"));
        server.verify();
    }
    @Test void refreshDoesNotRequireNewRefreshCredential() {
        server.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withSuccess(
                "{\"access_token\":\"access\",\"expires_in\":3600,\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
        assertFalse(google.refresh("refresh").containsKey("refresh_token"));
    }
    @Test void invalidGrantIsDefinitiveButProviderOutageIsNot() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant\"}"));
        assertThrows(GoogleCredentialException.class, () -> google.refresh("refresh"));
        server.verify(); server.reset();
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("private upstream body"));
        var exception = assertThrows(ApiException.class, () -> google.refresh("refresh"));
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode()); assertTrue(exception.getCause() == null);
        assertFalse(exception.getMessage().contains("private"));
    }
    @Test void missingExpiryOrNonBearerProviderResponseIsRejected() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"access\"}", MediaType.APPLICATION_JSON));
        assertThrows(ApiException.class, () -> google.exchangeCode("code", "v".repeat(43), "https://example.com"));
    }
    @Test void profileUsesProviderBearerAndDistinguishesPermissionLossFromQuotaErrors() {
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/profile"))
                .andExpect(header("Authorization", "Bearer provider-access"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"errors\":[{\"reason\":\"insufficientPermissions\"}]}}"));
        assertThrows(GoogleCredentialException.class, () -> google.profileEmail("provider-access"));
        server.verify(); server.reset();
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/profile"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"errors\":[{\"reason\":\"rateLimitExceeded\"}]}}"));
        assertThrows(ApiException.class, () -> google.profileEmail("provider-access"));
    }
    @Test void revokeIsFormPostAndAlreadyInvalidIsIdempotentButOutageFails() {
        server.expect(requestTo("https://oauth2.googleapis.com/revoke")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_token\"}"));
        google.revoke("refresh"); server.verify(); server.reset();
        server.expect(requestTo("https://oauth2.googleapis.com/revoke")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(ApiException.class, () -> google.revoke("refresh"));
    }
    @Test void configuredAllowlistRejectsWildcardsCredentialsAndNonLocalHttp() {
        for (String redirect : List.of("https://*.vercel.app/api/integrations/google/callback", "https://user@example.com/api/integrations/google/callback",
                "http://example.com/api/integrations/google/callback"))
            assertThrows(IllegalStateException.class, () -> new GoogleSettings(true, redirect));
        new GoogleSettings(true, "http://localhost:3000/api/integrations/google/callback")
                .checkRedirect("http://localhost:3000/api/integrations/google/callback");
    }

    @Test void enabledIntegrationRequiresClientCredentialsAndEncryptionKey() {
        assertThrows(IllegalStateException.class, () -> new GoogleOAuthService(RestClient.builder(), "client", "", true));
        assertThrows(IllegalStateException.class, () -> new CredentialCipher("", true));
        assertThrows(IllegalStateException.class, () -> new CredentialCipher("invalid", false));
        assertThrows(com.mymail.common.ApiException.class, () -> new GoogleSettings(false,
                "https://my-mail-front.vercel.app/api/integrations/google/callback").requireEnabled());
    }
}
