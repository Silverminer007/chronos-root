---
id: AE-027
title: "Push endpoints and event-driven notifications in the Rust backend"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-019-push-endpoints-and-event-notifications.md
verknuepft: []
---

# AE-027: Push endpoints and event-driven notifications in the Rust backend

Quelle: `kb/adr/ADR-2026-10-20-019-push-endpoints-and-event-notifications.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Java sends web push notifications from `WebPushService` (CDI observers) through `WebPushAdapter`
(VAPID, one send per subscription, `push_notification_log` row per attempt) and exposes
`PushResource`. The Rust backend had an unwired listener with invented event names/texts
(`appointment_created`, "Termin: ...") and an unencrypted `reqwest` sender.

## Entscheidung

1. **Parity set of events (ticket comment, option a).** Pushes are sent for exactly the events Java
   pushes for: message sent, moved, cancelled, participant added, group participant added,
   friendship request/accepted/declined/removed, group member added, participation status
   changed/invalid, pending-reminder, reminder, status recheck requested. Created/edited/deleted
   send nothing (as in Java). The old invented constants/handlers/texts were removed.
2. **Event types are the Java class names** (`AppointmentMovedEvent`, ...), the same names the
   appointments, groups and reminders services already publish on the `PostgresEventBus`. Payloads
   are the Java record components in snake_case with OIDC subjects (`push_notifications/events.rs`).
   `GroupMemberAddedEvent.user_id` and `AppointmentParticipationStatusChangedEvent.user_id` …

## Konsequenzen

- Every push event has an integration test (fake sender + database); the event bus wiring has one
  end-to-end test with `PostgresEventBus`.
- Known deviation: the `appointment` string uses the Rust REST serialisation (`start`/`end` RFC 3339
  as in the Rust appointment endpoints).
