---
title: ADR-2026-10-20-019: Change participant role endpoint
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
relates_to:
  - ADR-2026-10-14-017: Participant removal
  - ADR-2026-10-04-001: Add participant endpoint
---

## Context

Ticket #117 closes the Rust/Java parity gap for changing a participant's role. Java:
`PUT /api/v2/appointments/{id}/participation/{userId}/role`, body `ParticipantRoleDto {"role": "..."}`,
`AppointmentParticipationService.changeUserRole`. The ticket text says `/participants/...`; the ticket
discussion decided on the Java path `/participation/...`. The open questions of the specify step were
not answered in the ticket, so each is decided here with Java as the reference.

## Decision

1. **Path**: `PUT /api/v2/appointments/:id/participation/:userId/role` (Java path, per ticket comment of 2026-10-04).
2. **`{userId}`** is the OIDC id string, like every other Rust appointment handler (participation is keyed by
   `user_oidcid`; ADR 0011's UUID principal does not apply to the appointment tables, which follow the Java schema).
3. **Authorization**: the acting user must hold the `RESPONSIBLE` role in the appointment (not "creator" in the
   strict sense: the creator is just the first RESPONSIBLE, so a promoted participant may also change roles).
   Same check as Java `requireChangeParticipantRoleAtAppointment`. Failure is **403** (Java `ForbiddenException`).
   A new `ServiceError::Forbidden` carries this; the existing add/remove handlers still answer 400 for the same
   situation and are out of scope.
4. **Allowed roles**: every `UserRole` value (`NONE`, `GUEST`, `ATTENDANT`, `HELPER`, `RESPONSIBLE`), as Java's
   `UserRole.valueOf`. An unknown value is a 400 (Java fails with an unmapped exception).
5. **No admin bypass**: Java skips the check for admin requests; the Rust principal has no such flag yet.
   To be added together with the other endpoints when an admin principal exists.
6. **Errors** (as Java): unknown appointment 404 (any appointment status, like add/remove); target not a participant
   400 ("This user is not a participant of this event"); role unchanged 400 ("this is already the users role").
   Soft-delete: appointments are looked up with `find_by_id_any_status`, as the other participant endpoints.
7. **Response**: 200 with empty body.
8. **Event**: `AppointmentParticipationRoleChangedEvent` is published on the event bus after the update, with payload
   `appointment_id, target_user_id, acting_user_id, old_role, timestamp` (Java record carries the same data:
   appointmentId, targetUserOidcId, actingUserOidcId, oldRole). Java's `WebPushService` has no observer for this event
   today, and the push event type from #99 is not involved, so no push publisher is missing.

## Consequences

- Role changes are single-row `UPDATE`s on `appointment_participation`; no schema change.
- Parity gap kept: admin bypass (see 5).
- Tests: `rust-backend/tests/change_participant_role_test.rs` (service level against a real PostgreSQL) and a request
  deserialization unit test. Documented in Rustdoc of `handlers::change_participant_role`.
