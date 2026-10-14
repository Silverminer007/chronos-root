---
title: ADR-2026-10-14-015: Test database pool uses acquire_timeout instead of connect_timeout
date: 2026-10-14
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

`rust-backend/src/test_utils/db.rs` builds the test database pool with `PgPoolOptions`. The timeout from `TestDbConfig::connection_timeout` (default 30 s) bounds how long the helper waits for the database. It was first written as `.connect_timeout(...)`, which does not exist in sqlx 0.7.4 (the version pinned in `rust-backend/Cargo.lock`), so `test_utils` did not compile (commit `b810c25`).

The change to `.acquire_timeout(...)` is unrelated to the participation status endpoint of ticket #121. It is recorded here so the reason is not lost.

## Decision

Use `PgPoolOptions::acquire_timeout(config.connection_timeout)` in the test helper. The config field name `connection_timeout` is kept, so `TESTING.md` and existing callers do not change.

## Why this is equivalent for the test helper

In sqlx-core 0.7.4 (`src/pool/options.rs`), `connect_with` sets `deadline = now + acquire_timeout`. That deadline bounds the minimum-connection warm-up and the first `acquire()`, which is the connection attempt the test helper makes right after `connect()`. So for the helper's single eager connection, `acquire_timeout` bounds the same wait that `connect_timeout` would have bounded in later sqlx versions.

Later acquisitions from the pool also use `acquire_timeout`, so it is a bound on "get a connection", not a pure connect bound. Production pool settings are a separate concern and are configured in `rust-backend/src/database/mod.rs` (`DATABASE_ACQUIRE_TIMEOUT_SECS`, see ADR-0012).

## Alternatives considered

| Option | Result |
|---|---|
| Keep `.connect_timeout` and upgrade sqlx | Changes the dependency surface of the whole backend for a test helper. Out of scope. |
| Wrap `connect()` in `tokio::time::timeout(config.connection_timeout, ...)` | Gives an exact connect bound, but adds code to a test helper for no practical gain given the single-connection test pool. |
| **`acquire_timeout` (chosen)** | One-line change, compiles on sqlx 0.7.4, same deadline for the connect attempt. |

## Consequences

- If sqlx is upgraded to a version with `PoolOptions::connect_timeout`, the helper can switch back. Revisit this ADR at that point.
- The test helper's timeout semantics are "acquire timeout", not "connect timeout". Tests that rely on a precise connect timeout must not use this helper.
