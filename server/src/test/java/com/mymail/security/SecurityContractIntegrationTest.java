package com.mymail.security;

import com.mymail.MailApplication;
import com.mymail.api.model.*;
import com.mymail.authentication.config.SecurityConfig;
import com.mymail.authentication.service.Auth0Client;
import com.mymail.common.ApiException;
import com.mymail.integration.google.service.*;
import com.mymail.integration.google.repository.GoogleAccountRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@Testcontainers
@SpringBootTest(classes = {MailApplication.class, SecurityContractIntegrationTest.Keys.class}, properties = {
        "auth0.domain=tenant.invalid", "auth0.audience=mymail-test", "auth0.client-id=test", "auth0.client-secret=test",
        "resend.api-key=test", "google.oauth.enabled=true", "google.oauth.client-id=test-client",
        "google.oauth.token-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "logging.level.root=ERROR", "spring.main.banner-mode=off"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
class SecurityContractIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final KeyPair KEY = keyPair();
    private static final String REDIRECT = "https://my-mail-front.vercel.app/api/integrations/google/callback";
    private static final String VERIFIER = "v".repeat(43);
    private static final String CHALLENGE = OAuthStateService.challenge(VERIFIER);
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @TestConfiguration static class Keys {
        @Bean @Primary JwtDecoder testJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.getPublic()).build();
            decoder.setJwtValidator(SecurityConfig.validators("https://tenant.invalid/", "mymail-test"));
            return decoder;
        }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GoogleAccountRepository accounts;
    @Autowired GoogleRevocationRetry retries;
    @Autowired CredentialCipher cipher;
    @Autowired LegacyCredentialMigration legacyMigration;
    @MockitoBean GoogleOAuthService google;
    @MockitoBean Auth0Client auth0;
    final JsonMapper json = new JsonMapper();
    String alice;
    String bob;

    @BeforeEach void setup() throws Exception {
        jdbc.execute("truncate table google_oauth_states, google_accounts, users, revoked_access_tokens, authentication_rate_limits cascade");
        reset(google, auth0);
        alice = token("alice", "alice@example.com", Instant.now().plusSeconds(3600), "https://tenant.invalid/", "mymail-test", KEY);
        bob = token("bob", "bob@example.com", Instant.now().plusSeconds(3600), "https://tenant.invalid/", "mymail-test", KEY);
        when(auth0.login(anyString(), anyString())).thenReturn(new LoginResponse().accessToken(alice).tokenType("Bearer").expiresIn(3500L));
        when(auth0.register(anyString(), anyString())).thenReturn("auth0|registered");
        when(google.createAuthorizationUrl(anyString(), anyString(), anyString())).thenAnswer(inv ->
                "https://accounts.google.com/o/oauth2/v2/auth?state=" + inv.getArgument(0));
        when(google.exchangeCode(anyString(), anyString(), anyString())).thenAnswer(inv -> providerToken(true));
        when(google.refresh(anyString())).thenAnswer(inv -> providerToken(false));
        when(google.profileEmail(anyString())).thenReturn("gmail@example.com");
    }
    static KeyPair keyPair() {
        try { KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (GeneralSecurityException ex) { throw new IllegalStateException(); }
    }
    static String token(String subject, String email, Instant expiry, String issuer, String audience, KeyPair key) throws Exception {
        var claims = new JWTClaimsSet.Builder().subject(subject).issuer(issuer).audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(5))).jwtID(UUID.randomUUID().toString());
        if (email != null) claims.claim("https://mymail.com/email", email);
        if (expiry != null) claims.expirationTime(Date.from(expiry));
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) key.getPrivate())); return jwt.serialize();
    }
    Map<String, Object> providerToken(boolean refresh) {
        Map<String, Object> result = new HashMap<>(Map.of("access_token", "provider-access", "expires_in", 3600,
                "token_type", "Bearer", "scope", GoogleSettings.SCOPE));
        if (refresh) result.put("refresh_token", "provider-refresh");
        return result;
    }
    MvcResult perform(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return mvc.perform(request).andReturn();
    }
    MvcResult body(String path, Object body, String bearer) throws Exception {
        return perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), bearer);
    }
    void status(int expected, MvcResult result) {
        assertEquals(expected, result.getResponse().getStatus());
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        if (expected >= 400 && expected != 410) {
            var error = json.readTree(result.getResponse().getContentAsByteArray());
            assertEquals(5, error.size());
            assertFalse(error.get("error").asText().isBlank());
            assertEquals(expected, error.get("status").asInt());
            assertEquals(result.getRequest().getRequestURI(), error.get("path").asText());
            assertNotNull(OffsetDateTime.parse(error.get("timestamp").asText()));
            assertEquals("Request could not be completed.", error.get("message").asText());
            assertFalse(error.has("code"));
        }
    }
    String state(String user) throws Exception {
        // Provision profile before acquiring the integration's per-user row lock.
        status(200, perform(get("/api/users/me"), user));
        MvcResult result = body("/integrations/google/authorize", Map.of("redirectUri", REDIRECT,
                "codeChallenge", CHALLENGE, "codeChallengeMethod", "S256"), user);
        status(200, result);
        var response = json.readTree(result.getResponse().getContentAsString());
        String state = response.get("state").asText();
        assertTrue(state.matches("[A-Za-z0-9_-]{43,128}"));
        assertTrue(response.get("expiresIn").asInt() > 0 && response.get("expiresIn").asInt() <= 600);
        return state;
    }
    MvcResult complete(String user, String state) throws Exception { return complete(user, state, VERIFIER, REDIRECT); }
    MvcResult complete(String user, String state, String verifier, String redirect) throws Exception {
        return body("/integrations/google/complete", Map.of("code", "provider-code", "state", state,
                "codeVerifier", verifier, "redirectUri", redirect), user);
    }
    void connect() throws Exception { status(200, complete(alice, state(alice))); }
    String connection(String bearer) throws Exception {
        var result = perform(get("/integrations/google/status"), bearer); status(200, result);
        var body = json.readTree(result.getResponse().getContentAsString());
        String status = body.get("status").asText();
        if (status.equals("disconnected")) assertEquals(1, body.size());
        if (status.equals("connected")) {
            assertEquals(3, body.size()); assertEquals(1, body.get("scopes").size());
            assertEquals(GoogleSettings.SCOPE, body.get("scopes").get(0).asText());
        }
        return status;
    }

    @Test void authenticationContractsPreservePasswordAndCanonicalizeEmail() throws Exception {
        String password = "  original password  ";
        MvcResult result = body("/api/authentication/login", Map.of("email", " Alice@Example.COM ", "password", password), null);
        status(200, result);
        assertTrue(json.readTree(result.getResponse().getContentAsString()).get("expiresIn").asLong() == 3500);
        verify(auth0).login("alice@example.com", password);
        status(201, body("/api/authentication/signIn", Map.of("email", " NEW@Example.COM ", "password", password), null));
        verify(auth0).register("new@example.com", password);
        assertEquals("new@example.com", jdbc.queryForObject("select email from users where idp_id = 'auth0|registered'", String.class));
    }
    @Test void invalidCredentialsDuplicateValidationAndUpstreamFailureAreSafe() throws Exception {
        when(auth0.login(anyString(), anyString())).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        status(401, body("/api/authentication/login", Map.of("email", "a@example.com", "password", "wrong"), null));
        when(auth0.register(anyString(), anyString())).thenThrow(new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS"));
        status(409, body("/api/authentication/signIn", Map.of("email", "a@example.com", "password", "password123"), null));
        status(400, body("/api/authentication/signIn", Map.of("email", "a@example.com", "password", "short"), null));
        status(400, body("/api/authentication/login", Map.of("email", "invalid", "password", "x"), null));
        doThrow(new ApiException(HttpStatus.BAD_GATEWAY, "IDENTITY_PROVIDER_FAILURE")).when(auth0).login(anyString(), anyString());
        status(502, body("/api/authentication/login", Map.of("email", "a@example.com", "password", "x"), null));
    }
    @Test void databaseUniquenessRejectsDuplicateProfile() throws Exception {
        status(201, body("/api/authentication/signIn", Map.of("email", "same@example.com", "password", "password123"), null));
        when(auth0.register(anyString(), anyString())).thenReturn("auth0|other");
        status(409, body("/api/authentication/signIn", Map.of("email", "SAME@example.com", "password", "password123"), null));
    }
    @Test void rateLimitsReturnRetryAfter() throws Exception {
        for (int i = 0; i < 10; i++) status(200, body("/api/authentication/login", Map.of("email", "a@example.com", "password", "x"), null));
        MvcResult limited = body("/api/authentication/login", Map.of("email", "a@example.com", "password", "x"), null);
        status(429, limited); assertTrue(Integer.parseInt(limited.getResponse().getHeader("Retry-After")) > 0);
        for (int i = 0; i < 10; i++) state(alice);
        status(429, body("/integrations/google/authorize", Map.of("redirectUri", REDIRECT, "codeChallenge", CHALLENGE, "codeChallengeMethod", "S256"), alice));
    }
    @Test void realJwtVerificationRejectsExpiryIssuerAudienceSignatureAndMissingExp() throws Exception {
        status(401, perform(get("/api/users/me"), null));
        status(401, perform(get("/api/users/me"), "malformed"));
        for (String rejected : List.of(
                token("alice", "alice@example.com", Instant.now().minusSeconds(1), "https://tenant.invalid/", "mymail-test", KEY),
                token("alice", "alice@example.com", Instant.now().plusSeconds(600), "https://wrong.invalid/", "mymail-test", KEY),
                token("alice", "alice@example.com", Instant.now().plusSeconds(600), "https://tenant.invalid/", "wrong", KEY),
                token("alice", "alice@example.com", Instant.now().plusSeconds(600), "https://tenant.invalid/", "mymail-test", keyPair()),
                token("alice", "alice@example.com", null, "https://tenant.invalid/", "mymail-test", KEY))) {
            status(401, perform(get("/api/users/me"), rejected));
        }
        status(403, perform(get("/api/users/me"), token("missing-email", null, Instant.now().plusSeconds(600), "https://tenant.invalid/", "mymail-test", KEY)));
        var result = perform(get("/api/users/me"), alice); status(200, result);
        var profile = json.readTree(result.getResponse().getContentAsString());
        assertEquals(2, profile.size()); assertTrue(!profile.get("idpId").asText().isBlank());
        assertEquals("alice@example.com", profile.get("email").asText());
    }
    @Test void copiedTokenIsRejectedAfterIdempotentLogoutAndGmailRemainsLinked() throws Exception {
        connect(); String copiedToken = new String(alice);
        status(204, perform(post("/api/authentication/logout"), alice));
        status(204, perform(post("/api/authentication/logout"), copiedToken));
        status(401, perform(get("/api/users/me"), copiedToken));
        status(401, perform(get("/integrations/google/status"), copiedToken));
        assertEquals(1, accounts.count()); verify(google, never()).revoke(anyString());
        status(200, perform(get("/api/users/me"), bob));
    }
    @Test void googleRoutesRequireAuthenticationAndLegacyRoutesAreGone() throws Exception {
        status(401, body("/integrations/google/authorize", Map.of(), null));
        status(401, body("/integrations/google/complete", Map.of(), null));
        status(401, perform(get("/integrations/google/status"), null));
        status(401, perform(delete("/integrations/google"), null));
        status(410, perform(get("/integrations/google/connect"), null));
        status(410, perform(get("/integrations/google/callback").queryParam("code", "untrusted").queryParam("state", "untrusted"), null));
        verifyNoInteractions(google);
    }
    @Test void redirectAllowlistAndPkceMethodAreExact() throws Exception {
        for (String redirect : List.of("https://evil.invalid/api/integrations/google/callback", REDIRECT + "/", REDIRECT + "?x=1",
                "https://user@my-mail-front.vercel.app/api/integrations/google/callback", "https://preview.vercel.app/api/integrations/google/callback")) {
            status(400, body("/integrations/google/authorize", Map.of("redirectUri", redirect, "codeChallenge", CHALLENGE, "codeChallengeMethod", "S256"), alice));
        }
        status(400, body("/integrations/google/authorize", Map.of("redirectUri", REDIRECT, "codeChallenge", CHALLENGE, "codeChallengeMethod", "plain"), alice));
        status(400, body("/integrations/google/authorize", Map.of("redirectUri", REDIRECT, "codeChallenge", "bad", "codeChallengeMethod", "S256"), alice));
        assertEquals(0, jdbc.queryForObject("select count(*) from google_oauth_states", Integer.class));
    }
    @Test void wrongSubjectCannotConsumeOrInspectAnotherUsersTransactionOrAccount() throws Exception {
        String state = state(alice);
        status(403, complete(bob, state)); verify(google, never()).exchangeCode(anyString(), anyString(), anyString());
        assertEquals("disconnected", connection(bob));
        status(200, complete(alice, state));
        assertEquals("connected", connection(alice)); assertEquals("disconnected", connection(bob));
        status(204, perform(delete("/integrations/google"), bob)); assertEquals(1, accounts.count());
        status(403, complete(bob, state(bob))); assertEquals(1, accounts.count());
    }
    @Test void invalidExpiredReplayPkceMismatchAndCancelledStatesNeverExchange() throws Exception {
        status(400, complete(alice, "x".repeat(43)));
        String state = state(alice);
        status(400, complete(alice, state, "a".repeat(43), REDIRECT));
        status(400, complete(alice, state, VERIFIER, REDIRECT + "/"));
        jdbc.update("update google_oauth_states set expires_at = current_timestamp - interval '1 second'");
        status(400, complete(alice, state));
        String cancelled = state(alice); status(204, perform(delete("/integrations/google"), alice));
        status(400, complete(alice, cancelled));
        verify(google, never()).exchangeCode(anyString(), anyString(), anyString());
        String fresh = state(alice); status(200, complete(alice, fresh)); status(400, complete(alice, fresh));
        verify(google, times(1)).exchangeCode(anyString(), anyString(), anyString());
    }
    @Test void providerFailureConsumesTransactionAndScopeDenialDoesNotAttach() throws Exception {
        String failed = state(alice);
        when(google.exchangeCode(anyString(), anyString(), anyString())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "GOOGLE_PROVIDER_FAILURE"));
        status(502, complete(alice, failed)); status(400, complete(alice, failed));
        var denied = providerToken(true); denied.put("scope", "unrelated");
        doAnswer(inv -> denied).when(google).exchangeCode(anyString(), anyString(), anyString());
        status(400, complete(alice, state(alice))); assertEquals(0, accounts.count());
    }
    @Test void reconnectKeepsRefreshTokenAndCredentialsAreEncryptedAtRest() throws Exception {
        connect();
        String stored = jdbc.queryForObject("select refresh_token from google_accounts", String.class);
        assertTrue(stored.startsWith("v1:") && !stored.contains("provider-refresh"));
        assertTrue(cipher.decrypt(stored).equals("provider-refresh"));
        when(google.exchangeCode(anyString(), anyString(), anyString())).thenAnswer(inv -> providerToken(false));
        status(200, complete(alice, state(alice))); assertEquals(1, accounts.count());
        assertTrue(cipher.decrypt(jdbc.queryForObject("select refresh_token from google_accounts", String.class)).equals("provider-refresh"));
        assertEquals(0, jdbc.queryForObject("select count(*) from google_oauth_states where transaction_status = 'pending'", Integer.class));
    }
    @Test void refreshAndRevocationPersistReconnectButProviderOutageDoesNot() throws Exception {
        connect(); jdbc.update("update google_accounts set access_token_expires_at = current_timestamp - interval '1 minute'");
        assertEquals("connected", connection(alice)); verify(google).refresh(anyString());
        jdbc.update("update google_accounts set access_token_expires_at = current_timestamp - interval '1 minute'");
        when(google.refresh(anyString())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "GOOGLE_PROVIDER_FAILURE"));
        status(502, perform(get("/integrations/google/status"), alice));
        assertFalse(jdbc.queryForObject("select reconnect_required from google_accounts", Boolean.class));
        doThrow(new GoogleCredentialException()).when(google).refresh(anyString());
        assertEquals("reconnect_required", connection(alice));
        assertTrue(jdbc.queryForObject("select reconnect_required from google_accounts", Boolean.class));
    }
    @Test void permissionLossAndMissingRefreshRequireReconnection() throws Exception {
        connect(); jdbc.update("update google_accounts set access_token_expires_at = current_timestamp - interval '1 minute'");
        var lost = providerToken(false); lost.put("scope", ""); when(google.refresh(anyString())).thenAnswer(inv -> lost);
        assertEquals("reconnect_required", connection(alice));
        var result = perform(get("/integrations/google/status"), alice); status(200, result);
        assertEquals(0, json.readTree(result.getResponse().getContentAsString()).get("scopes").size());
        status(204, perform(delete("/integrations/google"), alice));
        when(google.exchangeCode(anyString(), anyString(), anyString())).thenAnswer(inv -> providerToken(false));
        status(400, complete(alice, state(alice))); assertEquals(0, accounts.count());
    }
    @Test void disconnectIsIdempotentAndOutageHasDurableRevocationRetry() throws Exception {
        connect(); String pending = state(alice);
        doThrow(new ApiException(HttpStatus.BAD_GATEWAY, "GOOGLE_PROVIDER_FAILURE")).when(google).revoke(anyString());
        status(502, perform(delete("/integrations/google"), alice));
        assertTrue(jdbc.queryForObject("select revocation_pending from google_accounts", Boolean.class));
        assertEquals("reconnect_required", connection(alice));
        status(409, complete(alice, pending));
        assertEquals(0, jdbc.queryForObject("select count(*) from google_oauth_states where transaction_status = 'pending'", Integer.class));
        doNothing().when(google).revoke(anyString());
        jdbc.update("update google_accounts set revocation_retry_at = current_timestamp - interval '1 second'");
        retries.retry();
        assertEquals(0, accounts.count()); status(204, perform(delete("/integrations/google"), alice));
        assertEquals("disconnected", connection(alice));
    }
    @Test void concurrentCompletionExchangesExactlyOnce() throws Exception {
        String pending = state(alice); AtomicInteger exchanges = new AtomicInteger();
        when(google.exchangeCode(anyString(), anyString(), anyString())).thenAnswer(inv -> { exchanges.incrementAndGet(); return providerToken(true); });
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> request = () -> { start.await(); return complete(alice, pending).getResponse().getStatus(); };
            Future<Integer> first = pool.submit(request); Future<Integer> second = pool.submit(request); start.countDown();
            var statuses = new ArrayList<>(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
            Collections.sort(statuses); assertEquals(List.of(200, 400), statuses);
        }
        assertEquals(1, exchanges.get()); assertEquals(1, accounts.count());
    }
    @Test void disconnectWaitsForInFlightCompletionThenRemovesItsCredentials() throws Exception {
        String pending = state(alice); CountDownLatch exchanging = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        when(google.exchangeCode(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            exchanging.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return providerToken(true);
        });
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> completion = pool.submit(() -> complete(alice, pending).getResponse().getStatus());
            assertTrue(exchanging.await(10, TimeUnit.SECONDS));
            Future<Integer> disconnection = pool.submit(() -> perform(delete("/integrations/google"), alice).getResponse().getStatus());
            release.countDown(); assertEquals(200, completion.get(20, TimeUnit.SECONDS)); assertEquals(204, disconnection.get(20, TimeUnit.SECONDS));
        }
        assertEquals(0, accounts.count()); verify(google).revoke(anyString()); status(400, complete(alice, pending));
    }
    @Test void legacyCredentialsAreEncryptedBeforeReadinessAndTamperingFailsClosed() throws Exception {
        connect();
        jdbc.update("update google_accounts set access_token = ?, refresh_token = ?", "legacy-access", "legacy-refresh");
        legacyMigration.run(null);
        assertTrue(cipher.decrypt(jdbc.queryForObject("select access_token from google_accounts", String.class)).equals("legacy-access"));
        String encrypted = cipher.encrypt("test-value");
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted.substring(0, encrypted.length() - 2) + "zz"));
    }

    @Test void registrationRateLimitMissingCallbackFieldsAndInvalidProfileClaim() throws Exception {
        doThrow(new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS")).when(auth0).register(anyString(), anyString());
        for (int i = 0; i < 5; i++) status(409, body("/api/authentication/signIn", Map.of("email", "duplicate@example.com", "password", "password123"), null));
        var limited = body("/api/authentication/signIn", Map.of("email", "duplicate@example.com", "password", "password123"), null);
        status(429, limited); assertTrue(Integer.parseInt(limited.getResponse().getHeader("Retry-After")) > 0);
        status(400, body("/integrations/google/complete", Map.of("code", "code", "codeVerifier", VERIFIER, "redirectUri", REDIRECT), alice));
        status(403, perform(get("/api/users/me"), token("bad-email", "invalid", Instant.now().plusSeconds(600), "https://tenant.invalid/", "mymail-test", KEY)));
    }
    @Test void disconnectClaimedFirstPreventsWaitingCompletionFromReconnecting() throws Exception {
        connect(); String pending = state(alice);
        CountDownLatch revoking = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> { revoking.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return null; }).when(google).revoke(anyString());
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> disconnect = pool.submit(() -> perform(delete("/integrations/google"), alice).getResponse().getStatus());
            assertTrue(revoking.await(10, TimeUnit.SECONDS));
            Future<Integer> completion = pool.submit(() -> complete(alice, pending).getResponse().getStatus());
            release.countDown(); assertEquals(204, disconnect.get(20, TimeUnit.SECONDS)); assertEquals(400, completion.get(20, TimeUnit.SECONDS));
        }
        assertEquals(0, accounts.count()); verify(google, times(1)).exchangeCode(anyString(), anyString(), anyString());
    }
    @Test void validExistingRefreshSurvivesReconnectRequiredAndInvalidExistingRefreshDoesNot() throws Exception {
        connect(); jdbc.update("update google_accounts set reconnect_required = true");
        when(google.exchangeCode(anyString(), anyString(), anyString())).thenAnswer(inv -> providerToken(false));
        status(200, complete(alice, state(alice))); assertEquals("connected", connection(alice));
        doThrow(new GoogleCredentialException()).when(google).refresh(anyString());
        status(400, complete(alice, state(alice))); assertEquals(1, accounts.count());
    }
    @Test void abandonedTransactionsAndExpiredDenylistEntriesAreCleaned() throws Exception {
        state(alice);
        jdbc.update("update google_oauth_states set expires_at = current_timestamp - interval '1 minute'");
        jdbc.update("insert into revoked_access_tokens(token_hash, expires_at) values (?, current_timestamp - interval '1 minute')", "0".repeat(64));
        new com.mymail.authentication.service.TokenRevocations(jdbc).cleanup();
        assertEquals(0, jdbc.queryForObject("select count(*) from google_oauth_states", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from revoked_access_tokens", Integer.class));
        assertEquals(4, jdbc.queryForObject("select count(*) from databasechangelog", Integer.class));
    }

    @Test void missingRoutesAndUnimplementedGoogleEmailApiReturnNotFoundDto() throws Exception {
        for (String path : List.of("/api/unknown", "/integrations/google/emails")) {
            var result = perform(get(path), alice);
            status(404, result);
            assertEquals("NOT_FOUND", json.readTree(result.getResponse().getContentAsByteArray()).get("error").asText());
        }
        verifyNoInteractions(google);
    }
}
