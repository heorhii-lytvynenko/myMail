# Authentication setup

Registration: `POST /api/authentication/signIn` with `email` and `password` creates an Auth0 database user, saves its full Auth0 ID (`auth0|...`) and email in the local `users` table, then requests an access token. Success returns HTTP 201 with `idpId`, `accessToken`, and `tokenType` (`Bearer`). An existing account is rejected with HTTP 409 when Auth0 reports a duplicate. Auth0 enforces uniqueness within the configured database connection. If token issuance fails after creation, the account and local profile remain; the user can log in later. Login: `POST /api/authentication/login` with the same fields returns an Auth0 access token. Send it as `Authorization: Bearer <accessToken>` to protected routes. `/api/users/me` creates the local application profile from the validated Auth0 subject and email claim; passwords are never stored locally.

Configure the variables in `.env.example`. `AUTH0_DOMAIN` must be the hostname only (no scheme or trailing slash). `AUTH0_CLIENT_ID` and `AUTH0_CLIENT_SECRET` belong to the confidential Regular Web Application; the secret stays on the server. `AUTH0_API_IDENTIFIER` is the API identifier, not the application client ID.

In Auth0:

1. Enable the database connection named by `AUTH0_CONNECTION` for the application, with signup enabled.
2. Enable the application's Password grant and use client secret POST authentication. This implementation preserves the existing password-based API and uses Auth0's password realm extension. MFA/challenge flows require additional UI/API support; browser applications can instead use Universal Login with Authorization Code + PKCE.
3. Configure the API to use RS256.
4. Deploy and attach a Post Login Action to the Login flow to supply the existing profile email claim:

```javascript
exports.onExecutePostLogin = async (event, api) => {
  if (event.user.email) {
    api.accessToken.setCustomClaim('https://mymail.com/email', event.user.email);
  }
};
```

The resource server checks token signature, expiry, issuer, and API audience. Only authentication endpoints, Swagger, the public example endpoint, and the Google callback are public. The Google callback authenticates its association through the existing single-use OAuth state.

References: [Auth0 custom signup](https://auth0.com/docs/libraries/custom-signup), [password realm flow and configuration](https://auth0.com/docs/get-started/authentication-and-authorization-flow/resource-owner-password-flow/call-your-api-using-resource-owner-password-flow).
