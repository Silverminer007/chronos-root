# Integration Testing Infrastructure

This document describes the test utilities and integration testing setup for the Chronos Rust backend.

## Overview

The testing infrastructure provides:

1. **PostgreSQL Test Containers** — Automatic spin-up and teardown of isolated PostgreSQL databases for each test
2. **Transaction Rollback** — Clean state between tests via `truncate` operations
3. **Test Fixtures** — Builders for creating reproducible test data
4. **Auth Helpers** — JWT token generation for testing protected endpoints
5. **HTTP Client Wrapper** — Simplified HTTP client for integration tests
6. **Database Setup/Teardown** — Automatic migrations and cleanup

## Quick Start

### Running Integration Tests

```bash
cd rust-backend

# Run all integration tests (requires Docker for testcontainers)
cargo test --test '*' -- --ignored

# Run a specific integration test
cargo test --test appointment_integration_test test_create_appointment_and_verify_in_db -- --ignored
```

### Basic Integration Test Example

```rust
#[tokio::test]
#[ignore]
async fn test_create_appointment() {
    // Setup test database
    let db = TestDb::new()
        .await
        .expect("Failed to initialize test database");
    
    // Create fixtures
    let fixtures = TestFixtures::new(db.pool().clone());
    
    // Create test user
    let user_id = fixtures
        .create_user("keycloak_123", "user@example.com")
        .await
        .expect("Failed to create user");
    
    // Create appointment
    let appointment_id = fixtures
        .create_appointment(
            user_id,
            &AppointmentFixture::new()
                .with_title("Team Meeting")
                .with_location(Some("Room 1"))
        )
        .await
        .expect("Failed to create appointment");
    
    // Verify in database
    let appointment: Appointment = sqlx::query_as(
        "SELECT id, title, description, start_time, end_time, location, creator_id, created_at, updated_at
         FROM appointments WHERE id = $1"
    )
    .bind(appointment_id)
    .fetch_one(db.pool())
    .await
    .expect("Failed to fetch appointment");
    
    assert_eq!(appointment.title, "Team Meeting");
}
```

## Test Utilities

### `TestDb` — Database Container Management

Handles PostgreSQL container lifecycle and connection pooling.

```rust
// Create test database with default config
let db = TestDb::new().await?;

// Or with custom config
let config = TestDbConfig {
    max_connections: 10,
    connection_timeout: Duration::from_secs(30),
};
let db = TestDb::with_config(config).await?;

// Get connection pool
let pool = db.pool();

// Begin transaction (for manual control)
let tx = db.begin().await?;

// Rollback all data
db.rollback_all().await?;
```

**Features:**
- Automatic container startup/shutdown
- Automatic schema migrations via `sqlx::migrate!`
- Connection pooling optimized for tests
- Data cleanup between tests

### `TestFixtures` — Test Data Builder

Creates reproducible test data with fluent builder pattern.

```rust
let fixtures = TestFixtures::new(db.pool().clone());

// Create user
let user_id = fixtures.create_user("keycloak_id", "email@example.com").await?;

// Create group
let group_id = fixtures.create_group(owner_id, "Team").await?;

// Create appointment
let appt_id = fixtures.create_appointment(
    creator_id,
    &AppointmentFixture::new()
        .with_title("Meeting")
        .with_description(Some("Desc"))
        .with_location(Some("Office"))
).await?;

// Add participant to appointment
let p_id = fixtures.add_participant(
    appointment_id,
    user_id,
    "ACCEPTED"
).await?;
```

### `AppointmentFixture` — Appointment Builder

Fluent builder for creating appointment test data.

```rust
let fixture = AppointmentFixture::new()
    .with_title("Daily Standup")
    .with_description(Some("Daily sync"))
    .with_location(Some("Conference Room A"))
    .with_times(start_time, end_time);

// Use with fixtures
let appt_id = fixtures.create_appointment(creator_id, &fixture).await?;
```

### `TestAuthHelper` — JWT Token Generation

Create test JWT tokens for authentication testing.

```rust
let auth_helper = TestAuthHelper::new();

// Create token for a user ID
let token = auth_helper.create_token("user_123")?;

// Create token with additional claims
let token = auth_helper.create_token_with_claims(
    "user_456",
    Some("user@example.com".to_string()),
    Some("Test User".to_string()),
)?;

// Create authorization header
let header = auth_helper.create_auth_header("user_789")?;
// Returns: "Bearer eyJ0eXAiOiJKV1QiLCJhbGc..."
```

### `TestHttpClient` — HTTP Request Helper

Simplified HTTP client for integration tests.

```rust
let client = TestHttpClient::new("http://localhost:8000".to_string());

// GET request
let response = client.get("/appointments").await?;

// GET with auth
let response = client.get_with_auth("/appointments", &auth_header).await?;

// POST with auth
#[derive(Serialize)]
struct CreateAppointmentRequest {
    title: String,
    start_time: DateTime<Utc>,
    end_time: DateTime<Utc>,
}

let req = CreateAppointmentRequest { /* ... */ };
let response = client.post_with_auth("/appointments", req, &auth_header).await?;

// DELETE with auth
let response = client.delete_with_auth("/appointments/123", &auth_header).await?;
```

## Test Patterns

### Pattern 1: Simple Database Verification

Verify that operations correctly persist to the database.

```rust
#[tokio::test]
#[ignore]
async fn test_create_and_fetch() {
    let db = TestDb::new().await?;
    let fixtures = TestFixtures::new(db.pool().clone());
    
    // Create
    let user_id = fixtures.create_user("id1", "test@example.com").await?;
    
    // Verify by querying
    let user: User = sqlx::query_as("SELECT * FROM users WHERE id = $1")
        .bind(user_id)
        .fetch_one(db.pool())
        .await?;
    
    assert_eq!(user.email, "test@example.com");
}
```

### Pattern 2: Multiple Related Entities

Test operations involving relationships between entities.

```rust
#[tokio::test]
#[ignore]
async fn test_appointment_with_participants() {
    let db = TestDb::new().await?;
    let fixtures = TestFixtures::new(db.pool().clone());
    
    // Setup entities
    let creator = fixtures.create_user("creator", "creator@example.com").await?;
    let participant = fixtures.create_user("participant", "participant@example.com").await?;
    
    let appt = fixtures.create_appointment(
        creator,
        &AppointmentFixture::new()
    ).await?;
    
    // Add relationship
    fixtures.add_participant(appt, participant, "ACCEPTED").await?;
    
    // Verify relationship
    let participants: Vec<_> = sqlx::query_as::<_, Participant>(
        "SELECT * FROM appointment_participants WHERE appointment_id = $1"
    )
    .bind(appt)
    .fetch_all(db.pool())
    .await?;
    
    assert_eq!(participants.len(), 1);
    assert_eq!(participants[0].user_id, participant);
}
```

### Pattern 3: Clean State Between Tests

Use `rollback_all()` to ensure test isolation.

```rust
#[tokio::test]
#[ignore]
async fn test_with_cleanup() {
    let db = TestDb::new().await?;
    let fixtures = TestFixtures::new(db.pool().clone());
    
    // First test
    fixtures.create_user("user1", "user1@example.com").await?;
    let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM users")
        .fetch_one(db.pool())
        .await?;
    assert_eq!(count.0, 1);
    
    // Cleanup
    db.rollback_all().await?;
    
    // Second test in same function
    let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM users")
        .fetch_one(db.pool())
        .await?;
    assert_eq!(count.0, 0);
}
```

### Pattern 4: JWT Token Testing

Test authentication with generated tokens.

```rust
#[tokio::test]
#[ignore]
async fn test_protected_endpoint_with_jwt() {
    let auth_helper = TestAuthHelper::new();
    let client = TestHttpClient::new("http://localhost:8000".to_string());
    
    let user_id = Uuid::new_v4();
    let token = auth_helper.create_token(&user_id.to_string())?;
    let auth_header = format!("Bearer {}", token);
    
    // Call protected endpoint with auth
    let response = client
        .get_with_auth("/appointments", &auth_header)
        .await?;
    
    assert_eq!(response.status(), 200);
}
```

## Database Setup and Migrations

Migrations are automatically run when a test database is created. Migration files live in:

```
rust-backend/migrations/
├── 001_initial_schema.sql    # Schema definition
└── ...
```

The `TestDb` initializer:
1. Starts a PostgreSQL container
2. Waits for it to be ready (max 3 seconds)
3. Runs all migrations from the `migrations/` directory
4. Returns a connection pool

## Parallel Test Execution

Tests can run in parallel safely because:

- Each test gets its own PostgreSQL container (isolated by testcontainers)
- Data is isolated per test (no shared database)
- Ports are automatically assigned

Run tests in parallel:

```bash
cargo test --test '*' -- --ignored --test-threads=4
```

## Running Tests Without Docker

Unit tests (not using `TestDb`) run without Docker:

```bash
# Run only unit tests (no Docker needed)
cargo test --lib

# This includes tests in:
# - src/error.rs
# - src/database/mod.rs (unit tests)
# - src/test_utils/*/tests/
# - etc.
```

## Troubleshooting

### Tests are slow

- First run downloads the PostgreSQL image (~200MB)
- Subsequent runs reuse the cached image
- Each test creates a new container, which adds ~1-2 seconds overhead

### "Docker is required but not running"

```bash
# Start Docker
docker daemon

# Or use rootless Docker
# See https://docs.docker.com/engine/security/rootless/
```

### Connection refused

Ensure the database is fully initialized before tests run:

```bash
# The TestDb automatically waits up to 3 seconds
# If it still fails, check Docker logs:
docker logs <container_id>
```

### Foreign key constraint violations

Truncate tables in dependency order. The `rollback_all()` method handles this:

```rust
db.rollback_all().await?;
```

## Best Practices

1. **Use fixtures builders** — Never hardcode test data; use builder patterns
2. **Call `rollback_all()`** — Between test scenarios in long tests
3. **Create users/groups/appointments** — In that order (respects foreign keys)
4. **Use meaningful keycloak IDs** — e.g., "test_user_scenario_1" for debugging
5. **Assert both positive and negative cases** — Test success and error paths
6. **Keep tests focused** — One test = one scenario
7. **Use `#[ignore]`** — Tests requiring Docker are marked with `#[ignore]`

## Future Improvements

- [ ] Transaction-scoped rollback (save point support)
- [ ] Distributed test setup (shared container pool)
- [ ] Test data snapshots (JSON fixtures)
- [ ] Performance profiling helpers
- [ ] Database state assertions helper
