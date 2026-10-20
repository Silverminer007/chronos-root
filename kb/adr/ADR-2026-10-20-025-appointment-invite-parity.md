# ADR-2026-10-20-025: Appointment invite parity (read access, friendship check, creator status)

- Status: accepted
- Ticket: #158 (found by the parity run in #137; Java is the reference)
- Amends: [ADR-2026-10-12-014](ADR-2026-10-12-014-rust-schema-identical-to-java.md) (view access no longer requires `RESPONSIBLE`; creator and listener insert use `PENDING`, not `APPROVED`)

## Context

Three differences between Rust and Java around inviting participants: invited users got `403` on `GET /api/v2/appointments/{id}` while the list showed the appointment, non-friends could be added as participants, and the creator was stored as `APPROVED` instead of `PENDING`.

## Decision

- **Read access:** `GET /appointments/{id}` allows every participant whose role is not `NONE` (Java `requireReadAppointment`); same rule as `GET .../messages`. Strangers and the creator-only rule from before are replaced; an unknown appointment is still `404`.
- **Friendship check:** `add_participant` checks, after the `RESPONSIBLE` check (Java order, `requireAddUserToAppointment`), that an `ACCEPTED` friendship between actor and target exists (`FriendshipRepository::are_friends`, shared with groups). Otherwise `403` "You can only add friends to appointments" and nothing is inserted. Adding group members already checked friendship (`403`), unchanged.
- **Module dependency:** `AppointmentRepository::are_friends` only delegates to `FriendshipRepository::are_friends` (`rust-backend/src/groups/repository.rs`), which is the single shared implementation. This adds a dependency from the `appointments` module on the `groups` module; the friendship query is deliberately not duplicated.
- **Update/delete unchanged:** `ensure_creator` (`appointments/handlers/mod.rs`, update and delete handlers) is still used, so `PUT`/`DELETE` stay creator-only while `GET` opens to all participants with a role other than `NONE`.
- **Creator status:** the creator is inserted as `RESPONSIBLE` with status `PENDING` (Java). `POST .../participation {"status":"APPROVED"}` by the creator therefore returns `200`.

## Consequences

- Tests: `rust-backend/tests/appointment_invite_parity_test.rs`; `appointment_mutations_test.rs` and the admin statistics test no longer assume an `APPROVED` creator.
- Not changed (out of scope): a non-`RESPONSIBLE` actor adding a participant still gets `400` instead of Java's `403`.
