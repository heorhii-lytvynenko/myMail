# Authentication and Google contract rollout

The source contract is [api.yaml](../api/src/api.yaml). Server errors follow the generated `ErrorDto`: `error` (stable code), `status` (HTTP status), `timestamp` (UTC ISO-8601), `path` (without query parameters), and a safe generic `message`. This replaces the old `code` field; callers should read `error`. MVC handlers and pre-controller security failures use the same serializer and retain no-store, WWW-Authenticate on 401, and Retry-After on 429. All protected endpoints take `Authorization: Bearer <application JWT>`; Google credentials stay on the backend. See [authentication setup](authentication.md) for credential login, email uniqueness, password policy, rate limits, errors, and logout semantics.

## Endpoints

| Method and path | Behavior |
| --- | --- |
| `POST /api/authentication/login` | 200 application JWT, Bearer, actual `expiresIn` |
| `POST /api/authentication/signIn` | 201 token fields plus `idpId` |
| `GET /api/users/me` | 200 exactly `idpId`, `email` |
| `POST /api/authentication/logout` | 204, immediate subsequent rejection of this exact copied token |
| `POST /integrations/google/authorize` | Authenticated JSON PKCE initiation, 200 authorization URL/state/600 seconds |
| `POST /integrations/google/complete` | Authenticated owner-bound completion, 200 connected/email/scopes after persistence |
| `GET /integrations/google/status` | Authenticated disconnected, connected, or reconnect_required |
| `DELETE /integrations/google` | Authenticated, idempotent 204 after provider revocation and local removal |
| `GET /integrations/google/emails` | Paginated OpenAPI contract only; no server controller implementation yet |
| `GET /integrations/google/connect` and `/callback` | 410; no code exchange, redirects, or account attachment |

Authorize accepts `redirectUri`, `codeChallenge` (43 base64url characters), and `codeChallengeMethod: "S256"`. It creates 32 random bytes of unpadded base64url state and persists the authenticated user, exact redirect, challenge/method, expiry and pending status. The only scope requested is `https://www.googleapis.com/auth/gmail.readonly`. The authorization URL uses exactly `https://accounts.google.com/o/oauth2/v2/auth`, response type code, offline access, and consent. Each security parameter occurs once; neither credentials nor a verifier are included.

Complete accepts `code`, `state`, `codeVerifier` (43-128 base64url characters), and the original `redirectUri`. It verifies the authenticated owner, exact allowlisted redirect, state freshness/expiry, and SHA-256 challenge. A conditional database update claims the transaction in an independently committed transaction before exchanging the code: provider failures and persistence failures cannot make a claimed transaction reusable. Wrong ownership returns 403; missing/expired/replayed/cancelled state, PKCE mismatch, rejected code, or denied scope returns 400. Invalid application sessions return 401. Other provider failures return safe 5xx.

Successful completion verifies the granted Gmail read-only permission and Gmail profile using the Google token, then persists encrypted credentials. Database uniqueness allows one Google connection per application user and prevents one Gmail identity being assigned to different application users. Gmail profile email is the provider identifier because this release requests no OpenID scope. Reconnection updates the existing row. When Google returns no new refresh token, the backend verifies and retains an existing refresh token for that same Gmail identity; it does not overwrite it with null or reuse another account's token.

The browser callback is Next.js, which owns the encrypted transaction cookie, browser/session binding, duplicate-parameter checks, cancellation UI and locale. The backend does not need the frontend cookie key and never accepts a body user ID. Denied/abandoned transactions expire within ten minutes; cleanup removes expired rows every minute.

## Status and disconnect

Disconnected returns exactly `{"status":"disconnected"}`. Connected returns exactly status/email/scopes with the single read-only scope. Reconnect-required returns status/email/scopes, with an empty scope list or the last known read-only permission. Credential row existence alone is insufficient: status refreshes near-expiry tokens and probes Gmail permission. Invalid/revoked refresh credentials or permission loss persist reconnect-required; provider outages return 5xx and do not mark a healthy connection revoked. An expired application JWT always returns 401 instead of a connection status.

All Google mutations/status checks acquire a PostgreSQL user-row lock. Concurrent completions exchange once. If completion wins the lock, a following disconnect removes that connection; if disconnect wins, it cancels pending transactions before any waiting completion can attach. Fresh authorization after a completed disconnect is a deliberate new connection.

Disconnect first cancels all pending authorization transactions and marks stored credentials unusable with a durable revocation-pending flag. It revokes the refresh token (or access token if no refresh token exists) and deletes the credential row only after success. Already-invalid provider tokens are treated as successfully revoked. An outage returns 502, preserves the encrypted row for retry, blocks reads/authorization/completion, and reports reconnect-required. It never reports successful provider revocation on a network failure.

A scheduled worker retries pending revocations every 60 seconds, up to 100 due users per pass, ordered by next attempt time; failures reschedule at least 60 seconds later so they cannot starve older unattempted entries; multiple workers serialize on the same user locks. The pending flag survives restarts. Repeating DELETE also retries immediately. Operators must monitor pending-row counts and provider configuration, especially persistent errors. There are no mailbox synchronization jobs in this repository to cancel; future jobs must honor the same user lock and pending/reconnect flags. Gmail sending is rejected because this integration has read-only permission.

## Migrations and secrets

Liquibase now runs the master changelog at startup; Hibernate validates instead of mutating the schema. The baseline SQL is idempotent for installations previously created with Hibernate. The security migration:

- adds the missing user version and Google audit columns;
- canonicalizes emails and enforces global case-insensitive email uniqueness, including deleted profiles;
- enforces one Google account per application user;
- adds PKCE transaction metadata/status, expiration indexes, and destroys all legacy pending OAuth states;
- adds the shared access-token revocation and authentication-rate-limit tables;
- adds scope/reconnect/revocation flags and widens credential columns for ciphertext.

Existing duplicate Google rows or email casing collisions deliberately fail migration. Resolve account ownership offline before deployment; no automatic destructive deduplication is attempted. Existing Google rows begin reconnect-required because their previously granted permission was not recorded. Existing refresh credentials can be retained on a verified reconnection.

Before application readiness, a transactional startup migrator obtains a PostgreSQL advisory lock and encrypts legacy plaintext Google access/refresh credentials using AES-256-GCM, a fresh 96-bit nonce, and authenticated version metadata. Subsequent JPA reads/writes use the encryption converter and reject unencrypted/corrupted data. Migration is idempotent. Supply the key even when disabling Google if legacy rows need encryption. Never serve old binaries alongside these migrations: old code expects plaintext and still exposes the retired callback.

Set backend configuration through a restricted secret manager:

| Variable | Value/purpose |
| --- | --- |
| `GOOGLE_INTEGRATION_ENABLED` | Defaults false; turn on only after compatible endpoints/callback verification |
| `GOOGLE_ALLOWED_REDIRECT_URIS` | Comma-separated exact approved callbacks; default production callback below |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Confidential Google web OAuth client credentials |
| `GOOGLE_TOKEN_ENCRYPTION_KEY` | Independently generated random 32-byte base64url key for backend token encryption |
| `AUTH0_*`, `DB_*`, `RESEND_API_KEY` | Existing backend configuration; see `.env.example` |

The production callback is `https://my-mail-front.vercel.app/api/integrations/google/callback`. Development may explicitly allow `http://localhost:3000/api/integrations/google/callback`. Register previews individually or keep integration disabled there. No wildcard domains, URL credentials, query/fragment variants, or Host-derived redirects are accepted.

Keep the encryption key separate from the database and from the frontend cookie key. Restrict runtime/database/backup access and encrypt database backups. The application currently has one versioned encryption key; rotation needs a coordinated re-encryption operation, not simply replacing the environment value. Protect and retain the key with backup recovery material. Startup encryption does not erase plaintext from historical backups or PostgreSQL WAL; handle their retention/encryption separately.

All upstream HTTPS calls preserve TLS verification. Auth0/Google token HTTP requests have five-second connect and ten-second read timeouts, with redirects disabled. Sensitive responses use no-store; provider bodies and exception causes are not returned or logged. Tomcat access logging is disabled and Spring request logging stays at INFO. Ensure Fly/edge/APM logs also omit query strings on both callback paths and redact Authorization, cookies, token bodies, code, state and verifier fields; application settings cannot control external logging. Do not enable HTTP wire logs or Hibernate parameter binding logs in production.

## Coordinated deployment order

1. Keep frontend `GOOGLE_INTEGRATION_ENABLED=false` and `BACKEND_LOGOUT_ENABLED=false`. Back up the database securely, provision the backend encryption key, and resolve uniqueness collisions. Drain/stop all old backend instances so the public callback and plaintext credential readers cannot remain reachable during migration.
2. Deploy the compatible backend endpoints first, with Liquibase migration and startup encryption. Confirm readiness/schema validation and verify login, registration, `/me`, copied-token rejection after logout, two-user isolation, PKCE/replay rejection, status and both disconnect races. No production deployment is performed by this repository change.
3. Register the exact Next.js callback above in Google Cloud OAuth's authorized redirect URI list. Update the backend exact allowlist; add localhost only in development. Configure Google consent/testing access and Gmail API availability.
4. Deploy the coordinated frontend with server-only `BACKEND_URL=https://mymail.fly.dev`, `FRONTEND_URL=https://my-mail-front.vercel.app`, and an independently generated random 32-byte base64url `GOOGLE_OAUTH_COOKIE_SECRET`. Never use NEXT_PUBLIC for secrets. Retain the frontend's host-only HttpOnly/SameSite=Lax/Secure-in-production `mymail_session`, no-store requests, and maximum eight-hour cookie bound.
5. After backend/callback smoke tests, enable Google on the backend, verify with a controlled account, then set frontend `GOOGLE_INTEGRATION_ENABLED=true` and `BACKEND_LOGOUT_ENABLED=true`. The frontend keeps its three-second logout timeout and clears the cookie even on failure. No guessed refresh endpoint or old callback fallback should be enabled.

Rollback must drain incompatible binaries and preserve the encrypted schema/key; redeploying old code against encrypted rows is unsafe. Disabling integration does not remove credentials; authenticated DELETE and the revocation retry worker remain available for cleanup.

## Verification and remaining external work

The automated suite uses PostgreSQL 17 through Testcontainers, real signed RSA application JWTs with the production validators, the actual Spring Security filter chain, Liquibase, JPA encryption, and mocked provider boundaries. Provider-client tests verify URL/form/header contracts and safe error classification without real accounts. Tests cover credential success/rejection/duplicates/validation/throttling, issuer/signature/audience/expiry, copied-token invalidation, redirect allowlisting, PKCE/ownership/expiry/replay/cancellation, concurrent completion, insufficient scope, refresh retention, status isolation/revocation/outages, idempotent disconnect, durable retries, both completion/disconnect orderings, cleanup and legacy encryption.

Verified locally on 22 October 2026: all 50 tests passed, OpenAPI validation passed, and the complete Gradle build (including compilation and the executable backend JAR) succeeded. The PostgreSQL integration suite executed all four Liquibase changesets and Hibernate schema validation. `git diff --check` passed.

Verified the ErrorDto server update on 25 October 2026: all 56 tests passed, including shared MVC/security error fields, safe messages and paths, HTTP status handling, rate-limit headers, and 404 for the unimplemented Google email route. A clean API generation, OpenAPI validation, and the full Gradle build also passed.

Run `./gradlew :server:test :api:openApiValidate build` with JDK 21 and Docker. Fresh-database startup now executes eight Liquibase changesets and Hibernate schema validation: the four authentication/security changesets plus the email tables and their account-cleanup function/trigger. Deployment secrets, live Auth0 policy, Google Cloud callback registration, provider consent, production data collision repair, edge-log redaction and live-provider smoke tests require the respective environments; automated tests do not claim to verify those external settings. The existing Auth0 password contract is preserved; the [hosted-login migration](authentication.md#hosted-login-migration-follow-up) remains a separate coordinated release.

Provider behavior follows [Google's web-server OAuth documentation](https://developers.google.com/identity/protocols/oauth2/web-server); JWT verification builds on [Spring Security's resource-server validators](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

## Cached email storage

`Email` and `GoogleEmailMessage` use JOINED inheritance: audit/version/soft-delete fields and shared headers live in `emails`; provider identifiers and account ownership live in `google_emails`, using the same UUID primary key. Provider message IDs are unique per Google account, not globally. Deleting an email parent cascades to its provider row. A BEFORE DELETE trigger on Google accounts deletes complete cached email rows, including soft-deleted rows that ORM restrictions would otherwise hide, in the same transaction as credential removal.

Storage bounds are application limits, not fixed Gmail or SMTP maxima: thread/message identifiers are 255 characters, complete From/To headers and unfolded subjects are 65,535, and snippets are 4,096. Entities validate these bounds and PostgreSQL enforces them independently. Complete address headers may include display names and multiple recipients. Imports must not silently truncate oversized values. No Gmail email API or synchronization job is implemented by these entity/migration changes.

The subsequent GET `/integrations/google/emails` implementation reads this PostgreSQL cache for the authenticated user's Google account. It accepts zero-based `page`, `size` (default 20, capped at 100), and repeated Spring `sort` parameters. Raw page/size parameters must each be a single valid 32-bit integer, with page at least zero and size at least one; invalid values return 400 `INVALID_EMAIL_PAGINATION` before Spring can default them. Page offsets exceeding JPA's integer range return 400 `INVALID_EMAIL_PAGE`. Sort fields are limited to `id`, `threadId`, `from`, `to`, `subject`, `snippet`, and `receivedAt`; unsupported fields return 400 `INVALID_EMAIL_SORT`. Default ordering is `receivedAt,desc`, with `id` appended for stable ordering. The response contains mapped email summaries, exact cached-row pagination totals, and `Cache-Control: no-store`. Soft-deleted rows are excluded.

Cached reads check verified application ownership and locally persisted connection flags without calling Google or locking the user row. A missing connection returns 404 `GOOGLE_DISCONNECTED`; known missing permissions, reconnect-required state, or pending revocation return 409 `GOOGLE_RECONNECT_REQUIRED`. Provider health and token refresh remain with status/live-provider operations, so a newly revoked Google grant may not be detected by a cached read until one of those operations updates connection state. This GET does not fetch or synchronize messages from Gmail, so an unpopulated cache returns an empty page. The separate access-token-based provider methods still support live Gmail reads.

When completion replaces the connected Gmail address, it deletes the old account's cached parent rows, including soft-deleted rows, before updating the connection. Foreign-key cascading removes their Google child rows. Cache removal and the replacement connection are committed in the same database transaction; reconnecting the same address retains its cache.
