---
id: AE-019
title: "Rust database schema identical to the Java schema"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-12
quellen:
  - kb/adr/ADR-2026-10-12-014-rust-schema-identical-to-java.md
verknuepft: []
---

# AE-019: Rust database schema identical to the Java schema

Quelle: `kb/adr/ADR-2026-10-12-014-rust-schema-identical-to-java.md` (Status im ADR: accepted, Datum: 2026-10-12). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

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

## Entscheidung

1. **Reference.** The final state after Flyway `V1.0.0 … V2.1.0` (`backend/src/main/resources/db/migration`).
   Each backend owns its database; the Rust schema follows the Java schema.
2. **One baseline migration.** `rust-backend/migrations/001_baseline.sql` creates that final schema
   (tables `appointment`, `appointment_participation`, `appointment_group_participations`,
   `friendship_requests`, `group_member`, `groups`, `message`, `push_notification_log`,
   `pushsubscription`, `settings`, `user_profiles`, with the same columns, types, nullability,
   defaults, constraints, indexes and sequences). The former `001`, `003`–`008` are removed; this
   rewrites the Rust migration history and is an explicit exception to C2025-09-28-004 (see
   [Exception](#exception-to-c2025-09-28-004-forward-only-migrations)). No data migration. Rust-only tables …

## Konsequenzen

- **No creator column.** Java has no `creator_id` on `appointment`; the creator is the participant
  with role `RESPONSIBLE`. Rust inserts the appointment and the creator's participation in one
  transaction. Authorization that used `creator_id` (delete, cancel) now requires the
  `RESPONSIBLE` participation; update requires `ATTENDANT` or above (unchanged). View access was
  amended by [ADR-2026-10-20-025](../../../kb/adr/ADR-2026-10-20-025-appointment-invite-parity.md): every participant
  whose role is not `NONE` may read, not only `RESPONSIBLE`.
  This supersedes sections 4 (status storage, fourth value) and 5 (creator participation via event after
  commit) of [ADR-2026-10-03-011](../../../kb/adr/ADR-2026-10-03-011-appointment-mutations.md); the event example and DTO
  example there (`creator_id` column semantics, `id: String`) are superseded as well. …
