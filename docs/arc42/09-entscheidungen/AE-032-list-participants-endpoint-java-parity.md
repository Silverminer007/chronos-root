---
id: AE-032
title: "List participants endpoint (Java parity)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-022-list-participants-endpoint.md
verknuepft: []
---

# AE-032: List participants endpoint (Java parity)

Quelle: `kb/adr/ADR-2026-10-20-022-list-participants-endpoint.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Issue #119 requires `GET /api/v2/appointments/{id}/participants` in the Rust backend. The Java reference is `AppointmentParticipationResource#getParticipants` and `AppointmentParticipationService#getParticipants`. The ticket thread left two questions open (`profile_picture_url` source, `via_group_*` tracking) and answered the delete question in favour of hard delete ([ADR-2026-10-14-017](ADR-2026-10-14-017-participant-removal.md)).

The open questions are resolved by what the Rust backend already has:

- `user_profiles` (the identity cache, [ADR-2026-10-20-019](ADR-2026-10-20-019-groups-friendships-endpoints.md) / `users::identity`) holds names and `profile_picture_url`.
- `appointment_participation.group_participation_id` exists in the schema that is identical to Java ([ADR-2026-10-12-014](ADR-2026-10-12-014-rust-schema-identical-to-java.md)).

## Entscheidung

### 1. Response

`200 OK` with a JSON array of `UserParticipantDto`: `user_id`, `name`, `profile_picture_url`, `role`, `status`, `via_group_id`, `via_group_name`. Java marks the DTO `@JsonInclude(NON_NULL)`, so `null` fields are omitted (`skip_serializing_if` on the Rust DTO). One element per participation row, ordered by participation id (Java has no explicit order; this keeps responses stable).

### 2. Data sources, no new schema or migration

- `name` / `profile_picture_url` come from `user_profiles` through `IdentityRepository::find_by_ids` (one query for all participants). A participant without a profile is the deleted-user sentinel: `name` is `Gelöschter Benutzer`, no picture. This answers ticket question 1: the picture is read from the existing `user_profiles` column, no new column and no Keycloak call. …

## Konsequenzen

- Contract parity harness cases can be added for this path without further code changes.
- When group participations are ported to the Rust write path, `via_group_*` starts to be filled without changing this endpoint.
