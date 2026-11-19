---
id: AE-028
title: "Change participant role endpoint"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-020-change-participant-role.md
verknuepft: []
---

# AE-028: Change participant role endpoint

Quelle: `kb/adr/ADR-2026-10-20-020-change-participant-role.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Ticket #117 closes the Rust/Java parity gap for changing a participant's role. Java:
`PUT /api/v2/appointments/{id}/participation/{userId}/role`, body `ParticipantRoleDto {"role": "..."}`,
`AppointmentParticipationService.changeUserRole`. The ticket text says `/participants/...`; the ticket
discussion decided on the Java path `/participation/...`. The open questions of the specify step were
not answered in the ticket, so each is decided here with Java as the reference.

## Entscheidung

1. **Path**: `PUT /api/v2/appointments/:id/participation/:userId/role` (Java path, per ticket comment of 2026-10-04).
2. **`{userId}`** is the OIDC id string, like every other Rust appointment handler (participation is keyed by
   `user_oidcid`; ADR 0011's UUID principal does not apply to the appointment tables, which follow the Java schema).
3. **Authorization**: the acting user must hold the `RESPONSIBLE` role in the appointment (not "creator" in the
   strict sense: the creator is just the first RESPONSIBLE, so a promoted participant may also change roles).
   Same check as Java `requireChangeParticipantRoleAtAppointment`. Failure is **403** (Java `ForbiddenException`).
   A new `ServiceError::Forbidden` carries this; the existing add/remove handlers still answer 400 for the same
   situation and are out of scope.
4 …

## Konsequenzen

- Role changes are single-row `UPDATE`s on `appointment_participation`; no schema change.
- Parity gap kept: admin bypass (see 5).
- Tests: `rust-backend/tests/change_participant_role_test.rs` (service level against a real PostgreSQL) and a request
  deserialization unit test. Documented in Rustdoc of `handlers::change_participant_role`.
