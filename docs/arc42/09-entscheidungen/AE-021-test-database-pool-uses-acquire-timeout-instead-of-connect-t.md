---
id: AE-021
title: "Test database pool uses acquire_timeout instead of connect_timeout"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-14
quellen:
  - kb/adr/ADR-2026-10-14-015-test-db-acquire-timeout.md
verknuepft: []
---

# AE-021: Test database pool uses acquire_timeout instead of connect_timeout

Quelle: `kb/adr/ADR-2026-10-14-015-test-db-acquire-timeout.md` (Status im ADR: accepted, Datum: 2026-10-14). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

`rust-backend/src/test_utils/db.rs` builds the test database pool with `PgPoolOptions`. The timeout from `TestDbConfig::connection_timeout` (default 30 s) bounds how long the helper waits for the database. It was first written as `.connect_timeout(...)`, which does not exist in sqlx 0.7.4 (the version pinned in `rust-backend/Cargo.lock`), so `test_utils` did not compile (commit `b810c25`).

The change to `.acquire_timeout(...)` is unrelated to the participation status endpoint of ticket #121. It is recorded here so the reason is not lost.

## Entscheidung

Use `PgPoolOptions::acquire_timeout(config.connection_timeout)` in the test helper. The config field name `connection_timeout` is kept, so `TESTING.md` and existing callers do not change.

## Konsequenzen

- If sqlx is upgraded to a version with `PoolOptions::connect_timeout`, the helper can switch back. Revisit this ADR at that point.
- The test helper's timeout semantics are "acquire timeout", not "connect timeout". Tests that rely on a precise connect timeout must not use this helper.
