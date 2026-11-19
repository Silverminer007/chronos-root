---
id: AE-039
title: "Appointment invite parity (read access, friendship check, creator status)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-025-appointment-invite-parity.md
verknuepft: []
---

# AE-039: Appointment invite parity (read access, friendship check, creator status)

Quelle: `kb/adr/ADR-2026-10-20-025-appointment-invite-parity.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Three differences between Rust and Java around inviting participants: invited users got `403` on `GET /api/v2/appointments/{id}` while the list showed the appointment, non-friends could be added as participants, and the creator was stored as `APPROVED` instead of `PENDING`.

## Entscheidung

- **Read access:** `GET /appointments/{id}` allows every participant whose role is not `NONE` (Java `requireReadAppointment`); same rule as `GET .../messages`. Strangers and the creator-only rule from before are replaced; an unknown appointment is still `404`.
- **Friendship check:** `add_participant` checks, after the `RESPONSIBLE` check (Java order, `requireAddUserToAppointment`), that an `ACCEPTED` friendship between actor and target exists (`FriendshipRepository::are_friends`, shared with groups). Otherwise `403` "You can only add friends to appointments" and nothing is inserted. Adding group members already checked friendship (`403`), unchanged.
- **Module dependency:** `AppointmentRepository::are_friends` only delegates to `FriendshipRepository::are_friends` (`rust-backend/src/groups/repository.rs`), which is the single shared implementation …

## Konsequenzen

- Tests: `rust-backend/tests/appointment_invite_parity_test.rs`; `appointment_mutations_test.rs` and the admin statistics test no longer assume an `APPROVED` creator.
- Not changed (out of scope): a non-`RESPONSIBLE` actor adding a participant still gets `400` instead of Java's `403`.
