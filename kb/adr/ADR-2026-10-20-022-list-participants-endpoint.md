---
title: ADR-2026-10-20-022: List participants endpoint (Java parity)
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Issue #119 requires `GET /api/v2/appointments/{id}/participants` in the Rust backend. The Java reference is `AppointmentParticipationResource#getParticipants` and `AppointmentParticipationService#getParticipants`. The ticket thread left two questions open (`profile_picture_url` source, `via_group_*` tracking) and answered the delete question in favour of hard delete ([ADR-2026-10-14-017](ADR-2026-10-14-017-participant-removal.md)).

The open questions are resolved by what the Rust backend already has:

- `user_profiles` (the identity cache, [ADR-2026-10-20-019](ADR-2026-10-20-019-groups-friendships-endpoints.md) / `users::identity`) holds names and `profile_picture_url`.
- `appointment_participation.group_participation_id` exists in the schema that is identical to Java ([ADR-2026-10-12-014](ADR-2026-10-12-014-rust-schema-identical-to-java.md)).

## Decision

### 1. Response

`200 OK` with a JSON array of `UserParticipantDto`: `user_id`, `name`, `profile_picture_url`, `role`, `status`, `via_group_id`, `via_group_name`. Java marks the DTO `@JsonInclude(NON_NULL)`, so `null` fields are omitted (`skip_serializing_if` on the Rust DTO). One element per participation row, ordered by participation id (Java has no explicit order; this keeps responses stable).

### 2. Data sources, no new schema or migration

- `name` / `profile_picture_url` come from `user_profiles` through `IdentityRepository::find_by_ids` (one query for all participants). A participant without a profile is the deleted-user sentinel: `name` is `Gelöschter Benutzer`, no picture. This answers ticket question 1: the picture is read from the existing `user_profiles` column, no new column and no Keycloak call.
- `via_group_id` / `via_group_name`: Java stores the **group id** in `group_participation_id` and resolves the name through `groups`. The Rust query does the same with a `LEFT JOIN groups`. This answers ticket question 2 without a migration. The Rust write path for group participations (`add_group`) does not yet copy group members into `appointment_participation`, so rows with a group are only produced by Java-written data until that gap is ported (not part of this ticket). A group row whose group no longer exists yields no `via_group_name` (Java would fail with a 500 there).

### 3. Authorization

The caller needs a participation with a role other than `NONE`; otherwise `403` (Java: `AuthorizationService#requireReadAppointment`, `UserRole.NONE` for non-participants). Unknown appointments therefore also give `403`, like Java. The Java admin bypass (`isAdminRequest`) is not ported: the Rust backend has no admin API yet.

### 4. Delete behaviour

No soft-delete filter. Removed participants are hard-deleted ([ADR-2026-10-14-017](ADR-2026-10-14-017-participant-removal.md)) and so no longer appear. Appointments in status CANCELLED or DELETED are not filtered, because Java does not check the appointment status either (same exception as [ADR-2026-10-14-016](ADR-2026-10-14-016-participation-status-endpoint.md)).

### 5. Errors and events

`ServiceError::Forbidden` is new and maps to `AppointmentError::Unauthorized` (403). The endpoint only reads; it publishes no event, so no push publisher is missing for it (ticket note from #99).

## Consequences

- Contract parity harness cases can be added for this path without further code changes.
- When group participations are ported to the Rust write path, `via_group_*` starts to be filled without changing this endpoint.
