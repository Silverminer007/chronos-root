# ADR-2026-10-20-027: System messages, group members as participants, admin message id (Java parity)

- Status: accepted
- Ticket: #159 (found by the parity run of #137; Java is the reference)

## Context

The harness steps `list messages`, `delete appointment`, `admin change participation status` and `admin send message` differed between Java and Rust.

## Decision

- **System messages are written synchronously in `AppointmentService`**, not by an event-bus listener. Java stores them in observers of `AppointmentParticipationStatusChangedEvent` (`@ObservesAsync`) and `AppointmentCancelledEvent` (`@Observes`, same call chain). The Rust event bus is asynchronous (Postgres events, own task), so a listener would make `GET .../messages` right after an RSVP racy. The event is still fired for the push notifications. The write follows the database change and is not tied to an event publisher (`AppointmentService::new` without a bus writes it too).
  - RSVP / admin status change: `"<Name> hat zugesagt"` (APPROVED) or `"<Name> hat abgesagt"`, sender is the user whose status changed (Java: `actingUserOidcId` of the event, which `changeParticipationStatus` sets to the target user).
  - Cancel: `"<Name> hat diesen Termin abgesagt. Er wird nicht stattfinden!"`, sender is the canceling user. `<Name>` is `UserIdentity::name()` from `user_profiles` ("Gelöschter Benutzer" without profile).
  - Not in scope: Java's third observer (`AppointmentMovedEvent`, "hat diesen Termin um n Stunden verschoben") is not ported; it is not part of #159.
- **Group members become participants.** `add_group_to_appointment` now also inserts every member of the group as `PENDING` participant with the role of the group link and `group_participation_id` = group id; members who already participate (e.g. the owner) keep their row (`ON CONFLICT DO NOTHING`), like Java's `existsByAppointmentAndUser`. One `INSERT ... SELECT`, so the group link and its members need no per-user round trip. Because the rows carry `group_participation_id`, `remove_group` already removes them again, and the participant list shows `via_group_id/name`.
- **Admin `POST /appointments/message` 500:** `insert_message` took its id from `message_seq` (step 50) while `persist_message` and the column default (`BIGSERIAL`) use `message_id_seq`; as soon as a user message existed the two sequences produced the same id and the primary key failed. Both now use the column default (one id source).
- **`DELETE` after `cancel`:** already 200 on `main` (the repository `set_status` does not filter by status); it was recorded as gone in the parity run 7 (see ARCH-2026-10-20-001). A regression test pins it.

## Consequences

- Tests: `rust-backend/tests/system_messages_parity_test.rs`.
- The `#159` entries are removed from `contract-tests/allowlist.json`.
- Existing tests that count messages after RSVP/cancel see the extra system message.
