---
id: AE-044
title: "System messages, group members as participants, admin message id (Java parity)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-027-system-messages-and-group-participants.md
verknuepft: []
---

# AE-044: System messages, group members as participants, admin message id (Java parity)

Quelle: `kb/adr/ADR-2026-10-20-027-system-messages-and-group-participants.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

The harness steps `list messages`, `delete appointment`, `admin change participation status` and `admin send message` differed between Java and Rust.

## Entscheidung

- **System messages are written synchronously in `AppointmentService`**, not by an event-bus listener. Java stores them in observers of `AppointmentParticipationStatusChangedEvent` (`@ObservesAsync`) and `AppointmentCancelledEvent` (`@Observes`, same call chain). The Rust event bus is asynchronous (Postgres events, own task), so a listener would make `GET .../messages` right after an RSVP racy. The event is still fired for the push notifications. The write follows the database change and is not tied to an event publisher (`AppointmentService::new` without a bus writes it too).
  - RSVP / admin status change: `"<Name> hat zugesagt"` (APPROVED) or `"<Name> hat abgesagt"`, sender is the user whose status changed (Java: `actingUserOidcId` of the event, which `changeParticipationStatus` sets to the target user).
  - Cancel: `"<Name> hat diesen Termin abgesagt …

## Konsequenzen

- Tests: `rust-backend/tests/system_messages_parity_test.rs`.
- The `#159` entries are removed from `contract-tests/allowlist.json`.
- Existing tests that count messages after RSVP/cancel see the extra system message.
- Not atomic: the system message in `cancel_appointment` and `change_participation_status` is written after the status change as a separate statement (as Java, where the observers run outside the status transaction). If the message insert fails, the cancel/status change is already committed but the request returns 500, and a retry may hit a conflict (e.g. "already cancelled"). Accepted because a lost system message is cosmetic; the status change is the source of truth.
