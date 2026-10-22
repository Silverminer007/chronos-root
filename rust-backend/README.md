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

### Health Checks and Metrics

Health and metrics follow the Quarkus contract (see [ADR-2026-10-20-020](../kb/adr/ADR-2026-10-20-020-prometheus-metrics-health-parity.md)). None of these routes require authentication.

- `GET /q/health/live` - Liveness probe. Returns `200` with the SmallRye JSON body `{"status": "UP", "checks": []}`.
- `GET /q/health/ready` - Readiness probe. Runs `SELECT 1` against the database and returns `{"status": "UP", "checks": [{"name": "Database connections health check", "status": "UP"}]}` with `200`. If the database is unreachable it returns `503` with `"status": "DOWN"` (the check entry is `DOWN` as well).
- `GET /health` - Alternative health check endpoint, plain text `OK`.
- `GET /metrics` - Prometheus text format. Contains the Micrometer-compatible HTTP series `http_server_requests_seconds_{count,sum,bucket,max}` (labels `exception`, `method`, `outcome`, `status`, `uri`), process metrics (`process_uptime_seconds`, `process_start_time_seconds`, `system_cpu_count`, `process_resident_memory_bytes` on Linux) and DB pool metrics (`agroal_active_count`, `agroal_available_count`, `agroal_max_size`). `/metrics` itself is not recorded; JVM metrics are not emitted.

### Admin

`/api/v2/admin/*` requires the realm role `ADMIN_API` (claim `realm_access.roles`); a token without it gets an empty `403` (see [ADR-2026-10-20-023](../kb/adr/ADR-2026-10-20-023-admin-endpoints.md)).

- `POST /api/v2/admin/appointments`, `GET /api/v2/admin/appointments?page&size`, `POST /api/v2/admin/appointments/{id}/participants/groups`, `POST /api/v2/admin/appointments/participants/status`, `POST /api/v2/admin/appointments/message`
- `POST /api/v2/admin/friendship/befriend`
- `POST /api/v2/admin/groups`, `POST /api/v2/admin/groups/{groupId}/users/{userId}`
- `POST /api/v2/admin/push/test/{userId}`, `GET /api/v2/admin/push/log`
- `GET /api/v2/admin/stats`
- `GET /api/v2/admin/users`, `GET /api/v2/admin/users/{id}`, `DELETE /api/v2/admin/users/{id}` (Keycloak)

### Appointments

- `GET /api/v2/appointments/:id/participants` - Participants with roles and status. Requires a valid bearer token and a participation with a role other than `NONE`; otherwise `403` (also for unknown appointments, like Java).
  - Response: array of `{user_id, name, profile_picture_url, role, status, via_group_id, via_group_name}`; `null` fields are omitted. Removed participants (hard delete) are not listed. See [ADR-2026-10-20-022](../kb/adr/ADR-2026-10-20-022-list-participants-endpoint.md).
- `GET /api/v2/appointments/:id/participation/status` - Current user's participation status. Requires a valid bearer token. Always `200 OK`; any authenticated user may call it.
  - Response body: `{"status": "PENDING" | "APPROVED" | "REJECTED"}`
  - `PENDING` is returned when the user has no participation row for the appointment, including for unknown appointment IDs, non-participants, and `CANCELLED`/`DELETED` appointments (matches the Java backend; see [ADR-2026-10-14-016](../kb/adr/ADR-2026-10-14-016-participation-status-endpoint.md)).
  - Returns only the caller's own status; there is no way to query another user's status.
- `POST /api/v2/appointments/:id/messages` - Posts a message (`{"body": "..."}`, any body is accepted like in Java). Same authorization as the `GET`. Answers `200` with the stored message `{id, sender_id, sender_name, appointment_id, body, timestamp}` (`sender_name` is the caller's name from the token) and fires `MessageSentEvent {message_id}` (see [ADR-2026-10-20-023](../kb/adr/ADR-2026-10-20-023-post-appointment-message.md)).
- `PUT /api/v2/appointments/:id/participation/:userId/role` - Change a participant's role. Requires a valid bearer token; the caller must be `RESPONSIBLE` for the appointment. `:userId` is the target's OIDC ID.
  - Request body: `{"role": "NONE" | "GUEST" | "ATTENDANT" | "HELPER" | "RESPONSIBLE"}`
  - Response: `200 OK` with an empty body. Fires `AppointmentParticipationRoleChangedEvent` (with the old role).
  - Errors: `400` for an unknown role, a target that is not a participant, or a role the target already has; `403` if the caller is not `RESPONSIBLE`; `404` for an unknown appointment (see [ADR-2026-10-20-020](../kb/adr/ADR-2026-10-20-020-change-participant-role.md)).
- `GET /api/v2/appointments/:id/messages` - Messages of an appointment, newest first. Requires a valid bearer token and a participation in the appointment (any role except `NONE`, any status); otherwise `403`, also for unknown appointments (see [ADR-2026-10-20-022](../kb/adr/ADR-2026-10-20-022-list-appointment-messages.md)).
  - Response body: array of `{"id", "sender_id", "sender_name", "appointment_id", "body", "timestamp"}`; `timestamp` is ISO-8601 UTC like Java `Instant.toString()`.
  - `sender_name` comes from the `user_profiles` cache; senders without a profile are named `Gelöschter Benutzer`.
  - Sending (`POST`) is not part of this endpoint's ticket and not yet implemented.
- `DELETE /api/v2/appointments/:id/groups/:groupId` - Removes a group from an appointment (hard delete of the link and of the participants that came in through the group; directly added ones stay). Requires the `RESPONSIBLE` role (`403` otherwise, also for unknown appointments); `400` if the group is not linked; `200` with empty body on success (see [ADR-2026-10-20-024](../kb/adr/ADR-2026-10-20-024-appointment-remove-group.md)).

### Groups and friendships

All routes require a valid bearer token and follow the Java contract (see [ADR-2026-10-20-019](../kb/adr/ADR-2026-10-20-019-groups-friendships-endpoints.md)). Names are resolved from `user_profiles`.

- `GET|POST /api/v2/groups`, `DELETE|PATCH /api/v2/groups/:group`, `GET /api/v2/groups/:group/users`, `POST|DELETE /api/v2/groups/:groupId/user/:userId`
- `GET /api/v2/friendships/suggestions`, `POST /api/v2/friendships/requests`, `POST /api/v2/friendships/requests/:requestId/accept|decline`, `DELETE /api/v2/friendships/requests/:requestId`, `GET /api/v2/friendships/requests/incoming|outgoing`, `GET /api/v2/friendships/friends`, `DELETE /api/v2/friendships/friends/:friendId`

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
| `KEYCLOAK_URL` | Keycloak realm URL (token validation) | `http://localhost:8080/realms/chronos` |
| `AUTH_SERVER_AUDIENCE` | Required `aud` of access tokens; unset or empty: `aud` is not validated (like Quarkus) | _unset_ |
| `AUTH_SERVER_BASE_URL` | Keycloak base URL for the admin API and the account-action links (required) | – |
| `AUTH_SERVER_REALM` | Realm of the admin client | `master` |
| `AUTH_SERVER_CLIENT_ID` | Confidential client (client-credentials grant, required) | – |
| `AUTH_SERVER_CLIENT_SECRET` | Secret of that client (required) | – |

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
