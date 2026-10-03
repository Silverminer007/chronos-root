# ADR 0012: Rust Backend Configuration and Migration Safety

**Date:** 2026-10-03  
**Status:** Accepted  
**Issue:** #103 — Rust backend cannot start; migration 005 fails on empty database, DATABASE_URL ignored  

## Context

The Rust backend had three critical issues:

1. **Migration 005 fails on empty database** — The migration tried to create indexes with `WHERE deleted_at IS NULL` predicates on tables that don't have that column. It also referenced wrong column names (`user_id`, `friend_id`) instead of (`requester_id`, `recipient_id`) on the `friendships` table.

2. **Environment configuration ignored** — `init_pool(Default::default())` hardcoded the database URL to `postgres://chronos:chronos@localhost:5432/chronos` and exposed the password in logs. The `DATABASE_URL` environment variable was never read.

3. **No graceful startup failure** — Database connection errors caused panics with backtraces instead of clear error messages and non-zero exit codes.

## Decision

**Migrations:** Fix `005_performance_indexes.sql` to remove predicates on non-existing columns and use correct column names.

**Configuration:** Add `DatabaseConfig::from_env()` to read:
- `DATABASE_URL` (required in production, defaults to localhost in development)
- `DATABASE_MAX_CONNECTIONS` (default 16)
- `DATABASE_MIN_CONNECTIONS` (default 2)
- `DATABASE_ACQUIRE_TIMEOUT_SECS` (default 5)
- `DATABASE_RUN_MIGRATIONS` (default true; allows deployments to disable auto-migrations)
- `PORT` (default 8080)
- `APP_ENV` (defaults to production)

Log connection info without exposing the password (show host, port, database name only).

**Startup:** Exit non-zero with clear error message on config/connection/migration failures. Do not use `.expect()` that produces panic backtraces.

**Readiness check:** `/q/health/ready` returns 503 (SERVICE_UNAVAILABLE) if `SELECT 1` fails on the database pool; 200 (OK) if successful.

**Advisory lock:** `sqlx::migrate!` uses PostgreSQL advisory locks, so concurrent migrations from multiple instances starting simultaneously are safe.

## Rationale

- **Safety**: Migrations must apply to an empty database; predicates on non-existing columns are a footgun for new deployments and test environments.
- **Portability**: Reading from environment variables allows deployment in Kubernetes (Helm values injected as env vars) and local development without code changes.
- **Security**: Passwords should never appear in logs; `log_connection_info()` only logs host/port/database.
- **Observability**: Clear startup errors help operators debug deployment issues; readiness probes let Kubernetes know when the service is ready.
- **Idempotency**: `DATABASE_RUN_MIGRATIONS=false` lets a deployment disable auto-migrations if desired (e.g., to run them separately via a Job or hook).

## Implementation

- **`src/database/mod.rs`**: `DatabaseConfig::from_env()` replaces `Default::default()` in main; `log_connection_info()` redacts passwords
- **`src/main.rs`**: Use `DatabaseConfig::from_env()`, exit non-zero on error, implement `/q/health/ready` with database check
- **`migrations/005_performance_indexes.sql`**: Drop `WHERE deleted_at IS NULL` predicates, fix friendships column names
- **`Cargo.toml`**: Add `url` crate for parsing database URLs
- **Tests**: 
  - (1) **Non-ignored full-migration integration test** (`test_migrations_apply_to_empty_database`): Testcontainers-based test verifying all migrations apply successfully to an empty PostgreSQL database
  - (2) **Concurrent migration safety test** (`test_concurrent_migrators_on_same_database`): Two concurrent migrators on the same database succeed using PostgreSQL advisory locks
  - (3) **Password redaction verification** (`test_password_not_in_log_format`): Log output contains host, port, and database name but never exposes the password
  - (4) **Readiness probe behavior test** (`test_run_migrations_false_skips_migrations` + `test_migrations_apply_to_empty_database`): `/q/health/ready` returns 503 SERVICE_UNAVAILABLE if database pool cannot execute `SELECT 1`, and 200 OK if successful

## Consequences

- Deployments must set `DATABASE_URL` in production (development defaults to localhost)
- Concurrent migrations are safe due to PostgreSQL advisory locks
- Startup failures fail fast with clear messages instead of panics
- Readiness probe provides accurate database connectivity status
