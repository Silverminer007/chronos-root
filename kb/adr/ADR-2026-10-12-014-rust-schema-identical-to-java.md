---
title: ADR-2026-10-12-014: Rust database schema identical to the Java schema
date: 2026-10-12
status: Accepted
supercedes: 0011-user-id-uuid-in-principal.md (user id as `Uuid`) and the migration layout of 0012-rust-backend-configuration-and-migrations.md (`001`–`008`)
superceded_by: (none)
---

**Issue:** #95 — Rust: database schema identical to the Java schema (bigint ids, OIDC user ids)

## Context

The Rust re-implementation must be a drop-in replacement for the Java backend: clients (and the
contract test that sends the same requests to both backends) must not notice a difference in
identifiers. The Java contract exposes numeric ids (`"id": 12`) and Keycloak subject strings as
user ids; there is no `users` table (removed in Flyway V2.0.0, profiles live in `user_profiles`
since V2.1.0).

The Rust migrations `001`–`008` followed their own design (`UUID` primary keys, a `users` table,
`appointment_participants`, ...). Before #104 they could not be applied to an empty database:
`005_performance_indexes.sql` used `WHERE deleted_at IS NULL` on tables without that column. #104
fixed that, and `001`–`008` now apply cleanly to an empty database in order, but no test applied them,
so nothing noticed the design mismatch with Java.

## Decision

1. **Reference.** The final state after Flyway `V1.0.0 … V2.1.0` (`backend/src/main/resources/db/migration`).
   Each backend owns its database; the Rust schema follows the Java schema.
2. **One baseline migration.** `rust-backend/migrations/001_baseline.sql` creates that final schema
   (tables `appointment`, `appointment_participation`, `appointment_group_participations`,
   `friendship_requests`, `group_member`, `groups`, `message`, `push_notification_log`,
   `pushsubscription`, `settings`, `user_profiles`, with the same columns, types, nullability,
   defaults, constraints, indexes and sequences). The former `001`, `003`–`008` are removed; this
   rewrites the Rust migration history and is an explicit exception to C2025-09-28-004 (see
   [Exception](#exception-to-c2025-09-28-004-forward-only-migrations)). No data migration. Rust-only tables
   stay as further migrations (`002_create_events_table.sql`: event bus outbox).
3. **Ids are `BIGINT`**, `i64` in Rust, JSON numbers. Tables that Java created without a column
   default (`appointment`, `appointment_participation`, `appointment_group_participations`,
   `friendship_requests`, `group_member`) and the Java-`BIGSERIAL` tables (`groups`, `message`,
   `pushsubscription`, `settings`, `push_notification_log`) all have a Hibernate-style
   `<table>_seq` sequence (`INCREMENT BY 50`). Rust takes every new id from `nextval('<table>_seq')`
   — the same sequence Java uses — so ids are unique, but sparse (1, 51, 101, …). `users_seq`, a
   leftover of the removed users table, is kept for parity.
4. **Users are OIDC subjects.** No `users` table. A user is identified by the Keycloak `sub`
   (`VARCHAR(255)`, columns `*_oidcid`), profiles are cached in `user_profiles` (`oidc_id`).
   `PrincipalContext.user_id` is that string (`String`). The auth middleware still answers **401**
   for a missing/invalid token and for a blank subject; a subject no longer has to be a UUID.
5. **Enums are stored like Java.** `appointment_participation.role`/`status` and
   `friendship_requests.status` are strings (`RESPONSIBLE`, `APPROVED`, `PENDING`, `ACCEPTED`, …).
   `appointment.status` is the exception: Java maps `AppointmentStatus` without `@Enumerated`, so the
   column is `SMALLINT` holding the **ordinal** (`PLANNED=0, CANCELLED=1, DELETED=2,
   NOT_ENOUGH_ATTENDEES=3`). Rust encodes/decodes it (`AppointmentStatus::ordinal`/`from_ordinal`,
   a NULL status is read as PLANNED); the JSON still shows the enum name.
6. **Proof.** `schema/snapshot.sql` describes the shared tables, columns (with position), constraints,
   indexes and sequences, one sorted line each. `schema/java_schema_expected.txt` is the snapshot of
   the Java schema, regenerated with `scripts/regenerate-java-schema.sh` (applies the Flyway SQL to a
   throw-away PostgreSQL; no Java). `tests/schema_parity_test.rs` (not ignored, Testcontainers):
   - applies **all** Rust migrations to an empty PostgreSQL and compares the snapshot with the
     checked-in one,
   - applies the Flyway SQL files to a second empty PostgreSQL and checks that the checked-in
     snapshot still equals the result (so it cannot drift from the Java migrations),
   - checks that the migration directory is exactly baseline + Rust-only migrations, that no column is
     `uuid` and that there is no `users` table.
   Excluded from the comparison: Rust-only tables (`events`, `_sqlx_migrations`) and the
   `message_backup`/`settings_backup` tables Flyway V1.1.0 leaves behind (`CREATE TABLE AS SELECT`,
   no entity, no constraints).

## Exception to C2025-09-28-004 (forward-only migrations)

[C2025-09-28-004](../constraints/C2025-09-28-004-forward-migrations.md) makes a deployed migration immutable.
This decision changes the Rust chain: `001_initial_schema.sql` is replaced by `001_baseline.sql` (same
version, different content, so the sqlx checksum changes) and `003`–`008` are deleted. The exception covers
the Rust chain only; the Java Flyway chain is not touched. The constraint names Flyway, but the Rust backend
uses sqlx 0.7.4, which records every applied migration with its checksum in `_sqlx_migrations`; the rule
applies to it in the same way.

The exception rests on these facts (checked in the repository and against a PostgreSQL 15 server):

- **Production never ran the Rust chain.** `deployment/values.yaml` sets `rustBackend.enabled: false`, and
  `values-prod.yaml` does not enable it.
- **Staging may have run it.** `values-staging.yaml` enables the Rust backend with the `develop` image.
  Migrations run at startup unless `DATABASE_RUN_MIGRATIONS=false`, which staging does not set. The versions
  applied to the staging database cannot be read from the repository.
- **`005` failed before #104.** The pre-#104 `005_performance_indexes.sql` fails on an empty database
  (`column "deleted_at" does not exist`). A database that migrated before #104 therefore holds at most
  `001`–`004`. Since #104, `001`–`008` apply cleanly to an empty database in order. `008` never failed.
- **sqlx refuses the baseline on top of the old chain.** sqlx 0.7.4 returns `VersionMissing` for an applied
  version that is no longer in `migrations/`, and `VersionMismatch` for an applied version whose checksum
  changed (`sqlx-core/src/migrate/migrator.rs`). A staging database that applied `001`–`008` will not start
  with this chain.

**Condition for deployment:** before the Rust backend with this chain starts on staging, the staging Rust
database must be recreated, and its contents are lost. Production is not affected because it never ran the
chain. After this baseline, the rule of C2025-09-28-004 applies again: a Rust migration that has reached
production is immutable, and fixes are new migrations.

## Consequences

- **No creator column.** Java has no `creator_id` on `appointment`; the creator is the participant
  with role `RESPONSIBLE`. Rust inserts the appointment and the creator's participation in one
  transaction. Authorization that used `creator_id` (view, delete, cancel) now requires the
  `RESPONSIBLE` participation; update requires `ATTENDANT` or above (unchanged).
  This supersedes sections 4 (status storage, fourth value) and 5 (creator participation via event after
  commit) of [ADR-2026-10-03-011](ADR-2026-10-03-011-appointment-mutations.md); the event example and DTO
  example there (`creator_id` column semantics, `id: String`) are superseded as well.
- **`AppointmentCreatedEvent` listener stays, as an idempotent safeguard.** `AppointmentCreatedEvent`
  is still published after commit and `AppointmentParticipationListener` (`event_listeners.rs`) still
  handles it by inserting the creator as `RESPONSIBLE`/`APPROVED`. Because the service already inserted
  that row, the insert hits `uk_appointment_participation_user` and is a no-op (`ON CONFLICT DO NOTHING`).
  Authorization never depends on the listener. It can be removed in a follow-up without behaviour change.
- **Fields without a Java column are gone.** `groups` has only `groupname`/`owner_oidcid`
  (no description, no timestamps); `friendship_requests` has `created_at`/`responded_at`
  (no `updated_at`); `pushsubscription` has no timestamps; appointments have `createdat`/`lastupdate`.
  The corresponding Rust DTOs lose those fields. Final JSON shapes (names, nulls) are aligned
  with the Java contract in the follow-up endpoint tickets.
- **Constraints Java does not have are not enforced by the database anymore**: unique group member,
  unique friendship pair, unique (user, endpoint) push subscription, no self-friendship.
  Duplicate push subscriptions are still rejected in `PushSubscriptionRepository::create`: the check
  runs after a transaction-scoped advisory lock keyed on (user, endpoint), so concurrent subscribe
  requests for the same pair cannot both insert (`test_concurrent_create_same_subscription_inserts_once`).
  The rest belongs to the services of the contract tickets.
- **Soft delete** is the `appointment.status` (DELETED/CANCELLED) only; the `deleted_at` columns of the
  old Rust design are gone.
- Event payloads carry `appointment_id` as a JSON number (was a UUID string); consumers also accept strings.
- A change of the Java schema needs a new Flyway version there, a regenerated
  `schema/java_schema_expected.txt` and a matching Rust migration (`003_…`) — the parity test fails
  until all three agree.
- Tests that need a server (`TestDb`) read `TEST_DATABASE_URL`, then `DATABASE_URL`, default
  `postgres://postgres:postgres@localhost:5432`; each test creates and migrates its own database.
