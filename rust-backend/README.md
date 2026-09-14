# Chronos Rust Backend

A modern Rust web service for the Chronos appointment management system, built with Axum web framework and Tokio async runtime.

## Project Structure

This follows a feature-based, layered module layout for scalability and maintainability:

```
src/
├── main.rs              # Application entrypoint with Axum router
├── lib.rs               # Library root with module declarations
├── appointments/        # Appointment management feature
│   ├── mod.rs           # Feature module declarations
│   ├── handlers/        # HTTP request/response handlers (presentation layer)
│   ├── models/          # Domain data structures (domain layer)
│   └── services/        # Business logic and orchestration (application layer)
├── users/               # User management feature
│   ├── mod.rs
│   ├── handlers/
│   ├── models/
│   └── services/
├── groups/              # Group management feature
│   ├── mod.rs
│   ├── handlers/
│   ├── models/
│   └── services/
├── push_notifications/  # Push notification feature
│   ├── mod.rs
│   ├── handlers/
│   ├── models/
│   └── services/
└── reminders/           # Reminder scheduling feature
    ├── mod.rs
    ├── handlers/
    ├── models/
    └── services/
```

See `ARCHITECTURE.md` for detailed layer descriptions and how this mirrors the Quarkus backend architecture.

## Building

### Prerequisites

- Rust 1.70+ (install via [rustup](https://rustup.rs/))
- For Docker builds: Docker with `x86_64-unknown-linux-musl` target support (pre-configured in multi-stage Dockerfile)

### Local Development

```bash
cd rust-backend

# Build in debug mode
cargo build

# Run in development mode
cargo run

# Run tests
cargo test

# Check code without building
cargo check
```

### Docker Build

```bash
cd rust-backend
docker build -t chronos-rust-backend:latest .
```

## API Endpoints

### Health Checks

- `GET /q/health/live` - Liveness probe (returns 200 if service is running)
- `GET /q/health/ready` - Readiness probe (returns 200 if service is ready to handle traffic)
- `GET /health` - Alternative health check endpoint

## Authentication

The backend uses **Keycloak OIDC** for authentication. JWT tokens are validated against Keycloak's public key set. Protected routes require a valid `Authorization: Bearer <token>` header.

### Protected Routes

Protected routes automatically extract the user's OIDC ID via the `PrincipalContext` extractor:

```rust
async fn get_user_info(principal: PrincipalContext) -> Json<UserInfo> {
    // principal.user_id() contains the authenticated user's OIDC subject ID
    Json(UserInfo { user_id: principal.user_id().to_string() })
}
```

### Error Responses

- `401 Unauthorized` — Missing, invalid, or expired token
- Token validation is performed by the `auth_middleware` layer

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `RUST_LOG` | Logging level | `info` |
| `KEYCLOAK_URL` | Keycloak realm URL | `http://localhost:8080/realms/chronos` |

## Development Workflow

1. **Feature modules** are created under `src/` with `handlers/`, `models/`, and `services/` subdirectories
2. **Domain models** are defined in each feature's `models/` directory as Serde-serializable structs
3. **Business logic** is implemented in the feature's `services/` directory as async functions
4. **HTTP routes** are registered in the feature's `handlers/` and wired in `main.rs`
5. **Database and authentication** integration points are described in `ARCHITECTURE.md`

### Future Work

See `ARCHITECTURE.md` for planned integration points:
- Database layer (SQLx/Sqlc for type-safe PostgreSQL queries)
- Authentication layer (Keycloak OIDC integration)
- Event publishing (async event channels for side-effects like push notifications)
- Configuration management (environment variables and config files)

## Deployment

See `/deployment` directory for Kubernetes Helm chart configuration.
