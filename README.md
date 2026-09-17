# myMail Backend

**A backend API for a modern web email client with AI-assisted spam and ham classification.**

[Frontend](https://github.com/heorhii-lytvynenko/myMail_front) · [Architecture](docs/architecture.md) · [Report an issue](https://github.com/heorhii-lytvynenko/myMail/issues)

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-Kotlin_DSL-02303A?logo=gradle&logoColor=white)

> [!NOTE]
> myMail is under active development. APIs and setup instructions may change.

## About

myMail is a web-based email client designed to bring multiple email workflows into one application. This repository contains the Spring Boot backend, including user management, authentication, Gmail integration, email sending, persistence, and the API contract.

The project follows a modular monolith architecture so that domain boundaries remain clear while deployment stays simple. The AI classification component is intended to classify messages as spam or ham and can be extracted into a separate service as the system evolves.

## Features

- Auth0-based authentication and authorization
- Google OAuth connection flow
- Gmail message retrieval and synchronization
- Transactional email delivery through Resend
- PostgreSQL persistence with JPA
- OpenAPI-first API contract and generated interfaces
- Domain-oriented modular architecture
- Planned AI-assisted spam/ham classification

## Tech stack

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.1 |
| Security | Spring Security, OAuth 2.0 Resource Server, Auth0 |
| Email | Gmail API, Resend, Jakarta Mail |
| Data | Spring Data JPA, PostgreSQL 17 |
| API | OpenAPI Generator, Springdoc |
| Mapping | MapStruct |
| Build | Gradle with Kotlin DSL |
| Infrastructure | Docker Compose |

## Repository structure

```text
myMail/
├── api/        # OpenAPI specification and generated API models/interfaces
├── server/     # Spring Boot application and domain modules
├── docs/       # Architecture documentation
├── gradle/     # Version catalog and Gradle wrapper files
└── docker-compose.yml
```

The backend is organized by business domain, including authentication, users, email, and Google integration. See [the architecture document](docs/architecture.md) for the design principles and planned evolution.

## Getting started

### Prerequisites

- JDK 21
- Docker and Docker Compose
- Auth0 application and API
- Google Cloud OAuth credentials with Gmail API access
- Resend API key

### 1. Clone the repository

```bash
git clone https://github.com/heorhii-lytvynenko/myMail.git
cd myMail
```

### 2. Configure the environment

Copy the provided template and replace the placeholder values:

```bash
cp .env.example .env
```

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB` | PostgreSQL database name |
| `POSTGRES_USER` | PostgreSQL container user |
| `POSTGRES_PASSWORD` | PostgreSQL container password |
| `DB_USERNAME` | Application database user |
| `DB_PASSWORD` | Application database password |
| `AUTH0_DOMAIN` | Auth0 tenant domain |
| `AUTH0_API_IDENTIFIER` | Auth0 API audience |
| `RESEND_API_KEY` | Resend API key |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `REDIRECT_URI` | Google OAuth callback URL |

The database credentials used by the application must match the credentials configured for the PostgreSQL container.

### 3. Start PostgreSQL

```bash
docker compose up -d postgres
```

### 4. Run the backend

On macOS or Linux:

```bash
./gradlew :server:bootRun
```

On Windows:

```powershell
.\gradlew.bat :server:bootRun
```

The Gradle `bootRun` task loads variables from the root `.env` file.

## Build and test

```bash
./gradlew build
./gradlew test
```

## Related repository

The user interface is maintained separately in [myMail Frontend](https://github.com/heorhii-lytvynenko/myMail_front).

## Project status

The core backend foundation and provider integrations are being developed. Planned work includes completing mailbox workflows, background synchronization, richer email organization, and AI-powered classification.

## Author

Developed by [Heorhii Lytvynenko](https://github.com/heorhii-lytvynenko).
