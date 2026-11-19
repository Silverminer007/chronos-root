---
id: AE-041
title: "Test databases are cloned from a template and dropped by a guard"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-025-test-db-template-and-cleanup.md
verknuepft: []
---

# AE-041: Test databases are cloned from a template and dropped by a guard

Quelle: `kb/adr/ADR-2026-10-20-025-test-db-template-and-cleanup.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

`TestDb` (`rust-backend/src/test_utils/db.rs`) created one database `test_db_<uuid>` per test on the shared PostgreSQL server, ran all migrations in it and never dropped it. After two days of agent runs the host held about 5800 such databases (45 GB) and the system disk was 98 % full (ticket #163).

## Entscheidung

1. **Drop guard.** `impl Drop for TestDb` runs `DROP DATABASE ... WITH (FORCE)` on a separate thread with its own runtime (`Drop` is synchronous and may run inside a runtime or while unwinding from a panic). `FORCE` terminates sessions of pool clones that the test still holds. The test must keep the `TestDb` alive for its whole duration.
2. **Template database.** The migrated schema is built once into `test_template_<hash of migration versions and checksums>`, under an advisory lock, and each test clones it with `CREATE DATABASE ... TEMPLATE`. The template is built under a `test_db_` name and renamed when complete, so an aborted build is never used and is cleaned by the janitor. A changed migration changes the hash and yields a new template. `CREATE DATABASE` retries on SQLSTATE 55006 (template in use by a concurrent clone).
3 …

## Konsequenzen

- Per test the cost is one `CREATE DATABASE ... TEMPLATE` and one `DROP DATABASE`; migrations run once per migration set. On a busy server `DROP DATABASE` waits for a checkpoint, so concurrent test runs make the drop slow; the test then waits for it.
- Obsolete templates (after a migration change) stay on the server, one per migration set; they are small and carry the `test_template_` prefix. They can be dropped by hand.
- A test that drops its `TestDb` early and keeps using a pool clone fails with "database does not exist".
