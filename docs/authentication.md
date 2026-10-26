# Authentication setup

The credential contract remains `POST /api/authentication/login` and `POST /api/authentication/signIn`, with JSON `email` and `password`. Login returns 200 and registration returns 201. Both responses contain `accessToken`, `tokenType: "Bearer"`, and `expiresIn`; registration also returns `idpId`. The backend verifies the issued JWT and bounds `expiresIn` by its remaining `exp` and Auth0's `expires_in`, allowing for request time. It never invents a fixed session lifetime or issues frontend refresh tokens.

Emails are stripped of surrounding whitespace and lowercased using `Locale.ROOT` before validation, Auth0 calls, and persistence. Email uniqueness is case insensitive across application profiles, including deleted profiles: an existing email is reserved and cannot be registered again. A database index enforces this across concurrent requests. Existing casing collisions must be resolved before migrating. Passwords are passed unchanged; registration requires at least eight Unicode code points, and Auth0 applies the actual configured connection policy. Configure that policy in Auth0 rather than duplicating or weakening it here. See [Auth0 password policies](https://auth0.com/docs/authenticate/database-connections/password-options).

Registration creates an Auth0 database user, saves its full subject (`auth0|...`) locally, and obtains an access token. If profile persistence or token issuance fails after remote creation, registration is not a distributed transaction: the Auth0 account may remain. Do not repeatedly create accounts to recover; use login or reconcile the profile with an operator. Passwords are never stored locally.

`GET /api/users/me` requires an application JWT and returns exactly `idpId` and `email`. The resource server verifies RS256 signatures, issuer, API audience, expiry (required, with no grace after exp), nonempty subject, and the shared database revocation ledger. New local profiles require the validated email claim below; a missing or invalid claim returns 403, never a placeholder profile.

## Auth0 configuration

Use the variables in `.env.example`. `AUTH0_DOMAIN` is a hostname without a scheme or slash. The confidential Regular Web Application owns `AUTH0_CLIENT_ID` and `AUTH0_CLIENT_SECRET`; `AUTH0_API_IDENTIFIER` is the API audience, not that client ID.

1. Enable the database connection named by `AUTH0_CONNECTION` for the application, with signup enabled and the desired password policy.
2. Keep Password grant and client secret POST authentication enabled during this synchronization release. Existing login uses Auth0's password-realm extension; MFA/challenge flows need additional UI/API work.
3. Configure the API to use RS256.
4. Attach a Post Login Action supplying the existing email claim:

```javascript
exports.onExecutePostLogin = async (event, api) => {
  if (event.user.email) {
    api.accessToken.setCustomClaim('https://mymail.com/email', event.user.email);
  }
};
```

Errors use the generated `ErrorDto` with a stable `error` code, numeric HTTP `status`, UTC ISO-8601 `timestamp`, request `path` without query parameters, and a generic `message`: 400 validation/policy rejection, 401 invalid credentials/session, 403 authenticated access denied, 409 duplicates, 429 throttling with a positive `Retry-After`, and safe 5xx service failures. Sensitive success and error responses use `Cache-Control: no-store`.

Database-backed, fixed one-minute windows apply across backend instances: login 10/email and 100/direct peer IP; signup 5/email and 20/direct peer IP; Google authorization 10/application subject. Caller-supplied forwarding headers are not trusted. The frontend server may share a peer IP with other requests; size edge limits and these thresholds for production traffic. Auth0 throttling is also mapped to 429 with `Retry-After: 60`.

## Logout

`POST /api/authentication/logout` requires the same application Bearer token and returns 204 after storing its SHA-256 digest until expiry. Repeating logout with that still-unexpired token returns 204. Every subsequent protected request with a copied token is rejected, across backend instances and restarts sharing the database. Other issued tokens remain separate sessions. Expired tokens still return 401. Requests already admitted before logout are not retroactively cancelled.

Logout neither disconnects Gmail nor revokes an unrelated provider token. Database failures fail closed. A frontend timeout/network failure cannot guarantee that the revocation reached the backend; clear the browser cookie regardless, and do not claim guaranteed server invalidation in that case. Enable frontend `BACKEND_LOGOUT_ENABLED=true` only after deployment and verification.

## Hosted-login migration follow-up

This release intentionally retains working password login and registration. Migrate separately to Auth0 Universal Login with Authorization Code + S256 PKCE; see [Auth0's PKCE flow](https://auth0.com/docs/get-started/authentication-and-authorization-flow/authorization-code-flow-with-pkce).

The Next.js server should initiate Auth0 authorization with unpredictable, browser-bound state, a fresh verifier/challenge, the existing API audience, and OIDC nonce. Register its exact login callback and logout URLs in Auth0. Its callback must validate state, nonce, issuer and token claims, exchange the code server-side using the verifier and confidential client credentials, and retain the existing HttpOnly application-session cookie. Hosted signup replaces the credential registration form; hosted login enables Auth0's MFA/passkey flows without forwarding passwords through myMail. Preserve the API subject mapping and email Action. Keep the Google connection transaction separate from the Auth0 login transaction.

Define frontend account/error/locale redirects, session expiration, IdP SSO logout, and any deliberate refresh-token rotation strategy before switching. The application token denylist still applies to API logout; Auth0 SSO logout is a separate concern. Verify the hosted flow end to end, move both frontend login and signup, then disable Password grant and retire the credential endpoints in a separately announced change. No hosted-login or refresh fallback is enabled here.

See [security contract and rollout](security-contract.md) for Google endpoints, migrations, deployment order, and operational limits.
