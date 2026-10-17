# myMail backend

Requires Java 21 and Docker with Docker Compose. The frontend lives in a separate repository.

## Run locally

1. Copy `.env.example` to `.env` if you do not already have one, and configure its values.
   Existing `.env` files also need `AUTH0_CLIENT_ID` and `AUTH0_CLIENT_SECRET` for authentication.
   See [authentication setup](docs/authentication.md) for Auth0 configuration.
2. Start PostgreSQL from the repository root:

   ```powershell
   docker compose up -d
   ```

3. Start the API (this task loads the root `.env` file):

   ```powershell
   .\gradlew.bat :server:bootRun
   ```

Open [Swagger UI](http://localhost:8080/swagger-ui/index.html).
`GET http://localhost:8080/api/public` should return 200; `/api/private` requires an access token.
Authentication and email integrations require valid provider credentials.

## Verify

```powershell
.\gradlew.bat :server:test :server:bootJar
```

Stop the API with Ctrl+C. Stop PostgreSQL with `docker compose stop` (retains database data).
