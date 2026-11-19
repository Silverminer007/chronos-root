---
id: AE-036
title: "Remove group from appointment (Java parity)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-024-appointment-remove-group.md
verknuepft: []
---

# AE-036: Remove group from appointment (Java parity)

Quelle: `kb/adr/ADR-2026-10-20-024-appointment-remove-group.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

`DELETE /api/v2/appointments/{id}/groups/{groupId}` exists in Java (`AppointmentParticipationService#removeGroupFromAppointment`).
The ticket thread settled the open questions: wait for #95 (bigint ids, Java-identical schema) and #122 (add group), both on `main`, then implement only the DELETE behaviour.

## Entscheidung

- **No migration.** `appointment_group_participations` and `appointment_participation.group_participation_id` come from the baseline ([ADR-2026-10-12-014](../../../kb/adr/ADR-2026-10-12-014-rust-schema-identical-to-java.md)). `groupId` is `i64`.
- **Order of checks (as in Java):** (1) the caller needs the `RESPONSIBLE` role, otherwise `403` "You have to be responsible for this appointment"; an unknown appointment is a `403` too, because the caller's role is none. The admin service (`as_admin`) skips the check. (2) The group link `(appointment, group)` is deleted; if no row was deleted the answer is `400` "This group is not a participant of this appointment" (no 404, no no-op).
- **Hard delete, no soft delete.** Deleted: the link row, then every `appointment_participation` of this appointment with `group_participation_id = groupId` …

## Konsequenzen

- Tests: `rust-backend/tests/appointment_remove_group_test.rs`.
- The Rust `add_group` still does not copy group members into `appointment_participation` ([ADR-2026-10-20-022](../../../kb/adr/ADR-2026-10-20-022-list-participants-endpoint.md)); the delete already handles rows with `group_participation_id` written by Java.
