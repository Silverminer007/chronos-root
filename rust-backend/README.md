# Chronos Rust Backend

A modern Rust web service for the Chronos appointment management system, built with Axum web framework and Tokio async runtime.

## Project Structure

This follows a feature-based module layout for scalability and maintainability:

```
src/
├── main.rs              # Application entrypoint
├── lib.rs               # Library root with module declarations
├── appointments/        # Appointment management feature
│   ├── mod.rs
│   └── model.rs
├── users/               # User management feature
│   ├── mod.rs
│   └── model.rs
├── groups/              # Group management feature
│   ├── mod.rs
│   └── model.rs
├── push_notifications/  # Push notification feature
│   ├── mod.rs
│   └── model.rs
└── reminders/           # Reminder scheduling feature
    ├── mod.rs
    └── model.rs
```

## Building

### Prerequisites

- Rust 1.70+ (install via [rustup](https://rustup.rs/))
- A C compiler (for some dependencies)

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

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `RUST_LOG` | Logging level | `info` |

## Development Workflow

1. **Feature modules** are created under `src/` with their own `mod.rs` and `model.rs`
2. **HTTP routes** are registered in `main.rs`
3. **Domain models** live in each feature's `model.rs`
4. **Services** follow the same feature structure

## Future Enhancements

- [ ] Database integration (PostgreSQL)
- [ ] Authentication (OIDC/Keycloak)
- [ ] RESTful endpoints for appointments, users, groups
- [ ] Push notification service
- [ ] Reminder scheduling engine
- [ ] Metrics and observability (Prometheus)

## Deployment

See `/deployment` directory for Kubernetes Helm chart configuration.
