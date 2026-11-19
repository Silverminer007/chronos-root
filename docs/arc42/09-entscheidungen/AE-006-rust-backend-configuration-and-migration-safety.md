---
id: AE-006
title: "Rust Backend Configuration and Migration Safety"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-03
quellen:
  - kb/adr/0012-rust-backend-configuration-and-migrations.md
verknuepft: []
---

# AE-006: Rust Backend Configuration and Migration Safety

Quelle: `kb/adr/0012-rust-backend-configuration-and-migrations.md` (Status im ADR: accepted, Datum: 2026-10-03). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

The Rust backend had three critical issues:

1. **Migration 005 fails on empty database** — The migration tried to create indexes with `WHERE deleted_at IS NULL` predicates on tables that don't have that column. It also referenced wrong column names (`user_id`, `friend_id`) instead of (`requester_id`, `recipient_id`) on the `friendships` table.

2. **Environment configuration ignored** — `init_pool(Default::default())` hardcoded the database URL to `postgres://chronos:chronos@localhost:5432/chronos` and exposed the password in logs. The `DATABASE_URL` environment variable was never read.

3. **No graceful startup failure** — Database connection errors caused panics with backtraces instead of clear error messages and non-zero exit codes.

## Entscheidung

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

**Startup:** Exit non-zero with clear error message on config/connection/migration failures. Do not use `.expect()` that produces panic backtraces. …

## Konsequenzen

- Deployments must set `DATABASE_URL` in production (development defaults to localhost)
- Concurrent migrations are safe due to PostgreSQL advisory locks
- Startup failures fail fast with clear messages instead of panics
- Readiness probe provides accurate database connectivity status
