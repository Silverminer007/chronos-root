# Chronos Rust Backend — Architecture

This document describes the layered architecture of the Rust backend and how it mirrors the existing Quarkus Java backend.

## Layered Architecture

The Rust backend follows a feature-based modular layout with explicit layers within each feature module, mirroring the Quarkus backend structure:

```
chronos_date_api (binary package)
│
├── handlers/      [presentation layer]
│   └── HTTP request/response mapping
├── models/        [domain layer]
│   └── Data structures and entities
└── services/      [application layer]
    └── Business logic and orchestration
```

### Layer Breakdown

Each feature module (`appointments/`, `users/`, `groups/`, `push_notifications/`, `reminders/`) contains:

#### Handlers Layer (Presentation)
- **File**: `{feature}/handlers/mod.rs`
- **Role**: JAX-RS equivalent; maps HTTP requests/responses
- **Pattern**: Axum route handlers; delegate business logic to services
- **Example** (future): `appointments/handlers/mod.rs` — `GET /appointments`, `POST /appointments`, etc.

#### Models Layer (Domain)
- **File**: `{feature}/models/mod.rs`
- **Role**: Core domain entities and data structures
- **Pattern**: Serde-serializable structs representing domain concepts
- **Example** (future): `appointments/models/mod.rs` — `Appointment`, `ParticipationStatus` structs

#### Services Layer (Application)
- **File**: `{feature}/services/mod.rs`
- **Role**: Business logic, orchestration, and side-effects
- **Pattern**: Async functions implementing the core domain logic
- **Example** (future): `appointments/services/mod.rs` — create appointment, send invitations, manage participation

### Comparison with Quarkus Backend

| Aspect | Quarkus | Rust |
|--------|---------|------|
| Presentation | `presentation/*.java` (JAX-RS resources) | `{feature}/handlers/mod.rs` (Axum routes) |
| Application | `application/*.java` (services, CDI beans) | `{feature}/services/mod.rs` (async functions) |
| Domain | `domain/*.java` (JPA entities) | `{feature}/models/mod.rs` (serde structs) |
| Infrastructure | `infrastructure/*.java` (repositories) | `{feature}/services/mod.rs` (TBD: database layer) |
| Framework | Quarkus (Java EE) | Axum + Tokio (async Rust) |
| Event Handling | CDI Events (`@Observe`) | TBD: event channel pattern |

## Key Architectural Principles

1. **Feature Cohesion**: Each feature module is self-contained with handlers, models, and services
2. **Dependency Injection**: Rust's type system and functions replace Java's CDI
3. **Async-First**: All I/O operations are async (Tokio runtime)
4. **Type Safety**: Rust's compiler enforces correctness at compile-time
5. **No Null Pointers**: Use `Option<T>` and `Result<T, E>` for error handling

## Future Integration Points

### Database (Infrastructure Layer)
- Planned: SQLx or Sqlc for type-safe queries
- Schema: Shared PostgreSQL with Quarkus backend (initially)
- Location: `{feature}/services/mod.rs` (repository functions) or new `infrastructure/` module

### Authentication (Security Layer) — IMPLEMENTED (Issue #25)
- **Status**: Keycloak OIDC token validation implemented and integrated
- **Components**:
  - `security/token.rs`: TokenValidator for JWT validation against Keycloak public keys
    - Automatic key caching with 1-hour TTL (configurable)
    - RS256 algorithm validation
    - Proper error handling (ExpiredSignature, InvalidSignature, KeyFetchError)
  - `security/principal.rs`: PrincipalContext for request-scoped user ID storage
    - Axum FromRequestParts extractor implementation
    - Equivalent to Java backend's PrincipalContext bean
  - `security/middleware.rs`: auth_middleware function for Axum integration
    - Validates Bearer tokens in Authorization header
    - Returns 401 for missing or invalid tokens
    - Injects PrincipalContext into request extensions
  - `security/scopes.rs`: TokenScope enum for future scope/role validation
- **Usage**: Apply `auth_middleware` via Axum middleware layer on protected routes
- **Future Work**: Authorization layer (role/scope validation, similar to Java's AuthorizationService)

### Event Publishing (Application Events)
- Planned: Tokio channels or async-broadcast crate
- Example: Appointment created → push notification sent asynchronously
- Location: New `events/` module

### Configuration (Config Layer)
- Planned: Environment variables or `config` crate
- Location: New `config/` module

## Development Workflow

1. **Add a new feature**: Create `src/{feature}/` with `handlers/`, `models/`, `services/` subdirectories
2. **Define domain models**: Implement structs in `{feature}/models/mod.rs`
3. **Implement business logic**: Add async functions in `{feature}/services/mod.rs`
4. **Expose HTTP endpoints**: Register routes in `{feature}/handlers/mod.rs` and wire in `main.rs`
5. **Test in isolation**: Unit test services; use `#[tokio::test]` for async tests

## Testing Strategy

- **Unit Tests**: Test services and models in `{feature}/services/mod.rs` and `{feature}/models/mod.rs`
- **Integration Tests**: Test handlers + services together (TBD)
- **End-to-End Tests**: Same as frontend suite (shared with Quarkus backend)

## Deployment

- **Container Image**: Multi-stage Dockerfile using `x86_64-unknown-linux-musl` target for fully static binary
- **Kubernetes**: Helm chart templates (`deployment/templates/rust-backend-*.yaml`)
- **Health Checks**: Liveness and readiness probes on `/q/health/live` and `/q/health/ready`

## Implementation Status

### Completed
- **Issue #23**: Project scaffolding & Axum setup - Feature modules with handlers/models/services structure
- **Issue #25**: Auth/security layer - Keycloak OIDC token validation and middleware integration

### In Progress / Planned
- **Issue #24**: Database layer - SQLx/PostgreSQL integration
- Event publishing (async channels for side-effects)
- Authorization layer (role/scope validation)
- Additional feature implementations (appointments, users, groups, etc.)
