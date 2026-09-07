# myMail System Architecture

## 1. Overview

myMail is a web-based email client with AI-assisted spam and ham classification.

The system consists of a frontend application and a backend API. The frontend is maintained in a separate repository, while the backend is implemented as a Spring Boot application.

The initial backend architecture is a modular monolith.

## 2. Architectural Style

The backend uses a modular monolith architecture.

All backend modules are deployed as a single Spring Boot application. Business responsibilities are separated into independent modules within the application.

The system is intentionally not split into microservices at the initial stage. Individual components may be extracted into separate services in the future if independent scaling, deployment, or resource requirements justify it.

## 3. Backend Modules

### Authentication

Responsible for authentication and authorization.

Responsibilities:

* User login
* Credential validation
* Token management
* Access control

### User

Responsible for user accounts and user-specific configuration.

Responsibilities:

* User management
* User preferences
* Connected email accounts

### Email

Responsible for email-related functionality.

Responsibilities:

* Email synchronization
* Reading emails
* Sending emails
* Email folders and labels
* Email metadata
* IMAP integration
* SMTP integration

### Classification

Responsible for AI-based email classification.

Responsibilities:

* Spam/ham classification
* Classification requests
* Classification results
* Communication with the AI classification component

The classification module should be isolated sufficiently to allow the AI component to be extracted into a separate service in the future.

### Infrastructure

Responsible for technical integrations and application-wide infrastructure.

Examples:

* Database configuration
* Persistence configuration
* Security configuration
* External service clients
* Application configuration

## 4. Package Structure

The backend follows a domain-oriented package structure.

```text
com.mymail
├── authentication
│   ├── controller
│   ├── service
│   ├── entities
│   └── repository
│
├── user
│   ├── controller
│   ├── service
│   ├── entities
│   └── repository
│
├── email
│   ├── controller
│   ├── service
│   ├── entities
│   └── repository
│
├── classification
│   ├── service
│   ├── entities
│   └── infrastructure
│
└── infrastructure
    ├── security
    ├── persistence
    └── configuration
```

The package structure is organized primarily by business domain rather than by technical layer across the entire application.

## 5. Module Responsibilities

Each module should have a clearly defined responsibility.

Modules should avoid directly accessing the internal implementation of other modules.

Communication between modules should occur through defined interfaces or application services where appropriate.

For example, the email module should not depend directly on a concrete AI implementation. It should communicate with the classification functionality through an abstraction.

## 6. Dependency Direction

The preferred dependency direction is:

```text
Controller
    ↓
Application / Service
    ↓
Domain / Entities
    ↓
Repository / Interfaces
    ↓
Infrastructure Implementations
```

Infrastructure implementations should not leak technical concerns into the business logic.

Where external systems are involved, the business layer should depend on abstractions rather than concrete external clients.

## 7. External Systems

The backend will interact with the following external systems:

```text
Frontend
   ↓ HTTP/HTTPS
server
   ├── PostgreSQL
   ├── IMAP
   ├── SMTP
   └── AI Classification Component
```

### PostgreSQL

Used for persistent application data such as users, connected accounts, email metadata, and classification results.

### IMAP

Used to retrieve and synchronize emails from external email providers.

### SMTP

Used to send emails through external email providers.

### AI Classification Component

Used to classify emails as spam or ham.

The AI component may initially be integrated directly with the backend and may later be extracted into an independent service.

## 8. Frontend

The frontend is maintained in a separate repository.

The frontend communicates with `server` through HTTP/HTTPS APIs.

The backend does not depend on the frontend implementation.

## 9. Initial Deployment Architecture

The initial deployment consists of a single backend application:

```text
                    ┌─────────────────┐
                    │    Frontend     │
                    │  Separate Repo  │
                    └────────┬────────┘
                             │
                         HTTP/HTTPS
                             │
                             ▼
                 ┌───────────────────────┐
                 │       server          │
                 │    Spring Boot App    │
                 │                       │
                 │ Authentication        │
                 │ User                  │
                 │ Email                 │
                 │ Classification        │
                 └───────┬───────┬───────┘
                         │       │
                         ▼       ▼
                    PostgreSQL  IMAP/SMTP
                         │
                         │
                         ▼
                 AI Classification
```

## 10. Architectural Principles

The following principles guide the initial implementation:

1. Prefer a modular monolith over premature microservices.
2. Organize code around business domains.
3. Keep business logic independent from infrastructure where practical.
4. Use interfaces to isolate external integrations.
5. Keep modules independently testable.
6. Avoid unnecessary coupling between modules.
7. Keep the initial deployment and operational model simple.
8. Design the classification component so it can be extracted later if required.

## 11. Future Evolution

The architecture is intentionally designed to support future evolution.

Potential future changes include:

* Extracting AI classification into a separate service.
* Introducing asynchronous processing for email classification.
* Adding message queues for background email synchronization.
* Scaling email synchronization independently from the API.
* Introducing additional AI capabilities beyond spam/ham classification.

These changes should only be introduced when justified by actual functional, performance, or operational requirements.
