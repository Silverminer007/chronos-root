---
title: ADR-2026-10-20-025: Test databases are cloned from a template and dropped by a guard
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

`TestDb` (`rust-backend/src/test_utils/db.rs`) created one database `test_db_<uuid>` per test on the shared PostgreSQL server, ran all migrations in it and never dropped it. After two days of agent runs the host held about 5800 such databases (45 GB) and the system disk was 98 % full (ticket #163).

## Decision

1. **Drop guard.** `impl Drop for TestDb` runs `DROP DATABASE ... WITH (FORCE)` on a separate thread with its own runtime (`Drop` is synchronous and may run inside a runtime or while unwinding from a panic). `FORCE` terminates sessions of pool clones that the test still holds. The test must keep the `TestDb` alive for its whole duration.
2. **Template database.** The migrated schema is built once into `test_template_<hash of migration versions and checksums>`, under an advisory lock, and each test clones it with `CREATE DATABASE ... TEMPLATE`. The template is built under a `test_db_` name and renamed when complete, so an aborted build is never used and is cleaned by the janitor. A changed migration changes the hash and yields a new template. `CREATE DATABASE` retries on SQLSTATE 55006 (template in use by a concurrent clone).
3. **Janitor.** Once per test process, `drop_stale_databases` drops databases named `test_db_<unix seconds>_*` that are older than one hour and have no connection (at most 20 per run, because every `DROP DATABASE` waits for a checkpoint). Names without that shape and all other databases are never touched. The creation time is encoded in the name because PostgreSQL does not store it.
4. **Check.** CI counts `^test_db_[0-9]+_` databases after the integration tests and fails when any remain; the `tests/test_db_cleanup_test.rs` tests cover the guard, the panic case and the janitor.

5. **Assumptions about the server and the role.** The code relies on both; a change of either breaks the tests and must be checked against this list:
   - **PostgreSQL 13 or newer.** `DROP DATABASE ... WITH (FORCE)` does not exist before 13. CI uses `postgres:16`, the shared host server runs 15.
   - **Role privileges.** The test role needs `CREATEDB` (`CREATE DATABASE ... TEMPLATE`, and `ALTER DATABASE ... RENAME` of the template, which also requires that the role owns the database it renames). It must own the test databases it drops, or be a superuser or member of `pg_signal_backend`, because `FORCE` terminates the sessions that still use the database. `pg_advisory_lock` (template build lock) needs no privilege; it is held on the admin connection and is server-wide, so all test processes that share a server serialize on it. The janitor ignores a failed `DROP DATABASE` and moves on, so a role that may not drop a stale database leaves it behind silently.

## Alternatives considered

| Option | Result |
|---|---|
| Drop in a test-binary-level teardown | Needs a custom harness (`harness = false`) or a `ctor` dependency for all ~20 test binaries; a per-value guard is simpler and also covers panics. |
| Per-process template | Re-runs the migrations once per test binary and needs its own cleanup; a shared, hash-named template is built once. |
| Read the age from `pg_stat_file` on the database directory | Needs superuser and file access on the server. |
| Janitor drops legacy `test_db_<uuid>` databases | Their age is unknown. They are removed by hand (coordinator); new names always carry a timestamp. |

## Consequences

- Per test the cost is one `CREATE DATABASE ... TEMPLATE` and one `DROP DATABASE`; migrations run once per migration set. On a busy server `DROP DATABASE` waits for a checkpoint, so concurrent test runs make the drop slow; the test then waits for it.
- Obsolete templates (after a migration change) stay on the server, one per migration set; they are small and carry the `test_template_` prefix. They can be dropped by hand.
- A test that drops its `TestDb` early and keeps using a pool clone fails with "database does not exist".
