# ADR-2026-10-20-019: Push endpoints and event-driven notifications in the Rust backend

- Status: accepted
- Ticket: #99 (map #21: Rust re-implementation, Java is the reference)

## Context

Java sends web push notifications from `WebPushService` (CDI observers) through `WebPushAdapter`
(VAPID, one send per subscription, `push_notification_log` row per attempt) and exposes
`PushResource`. The Rust backend had an unwired listener with invented event names/texts
(`appointment_created`, "Termin: ...") and an unencrypted `reqwest` sender.

## Decisions

1. **Parity set of events (ticket comment, option a).** Pushes are sent for exactly the events Java
   pushes for: message sent, moved, cancelled, participant added, group participant added,
   friendship request/accepted/declined/removed, group member added, participation status
   changed/invalid, pending-reminder, reminder, status recheck requested. Created/edited/deleted
   send nothing (as in Java). The old invented constants/handlers/texts were removed.
2. **Event types are the Java class names** (`AppointmentMovedEvent`, ...), the same names the
   appointments, groups and reminders services already publish on the `PostgresEventBus`. Payloads
   are the Java record components in snake_case with OIDC subjects (`push_notifications/events.rs`).
   `GroupMemberAddedEvent.user_id` and `AppointmentParticipationStatusChangedEvent.user_id`
   (existing publishers) are accepted as the new member / acting user via serde aliases.
3. **Acting user on move/cancel.** Java's records carry `actingUserOidcId`; the Rust events did not.
   `AppointmentMovedEvent` (now with RFC 3339 `old_start`/`old_end` instead of epoch seconds, which
   map to Java's `Instant#toString`) and `AppointmentCancelledEvent` gained `acting_user_id`;
   `AppointmentService::update_appointment` / `cancel_appointment` take the acting user.
4. **Messages, recipients and settings are copied 1:1.** Payload JSON (`type`, `appointment` as a
   JSON *string* of the REST DTO without nulls, ...), the German title/body of the message push
   (`"<Name> schreibt zu <Termin>"`), the recipient filters (acting user excluded, `PENDING` only for the
   pending reminder, recheck to everyone) and the per-user settings check (`checkSetting`: role rank
   vs. `ALL/ATTENDANT/HELPER/RESPONSIBLE/DISABLED`, `group_member_added` ENABLED only) follow
   `WebPushService`/`SettingsService`. An unknown acting user, appointment or group ends the handling
   silently, like Java's early returns. User names come from `user_profiles`
   (`"<first> <last>"` trimmed, `"Gelöschter Benutzer"` when all profile fields are null).
5. **Sending is abstracted by `PushSender`** (`Ok(status)` = answer of the push service, `Err` = no
   answer). `WebPushSender` uses the `web-push` crate (RFC 8291 encryption, VAPID) with
   `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` / `VAPID_SUBJECT`; tests use a fake. `NotificationDispatcher`
   mirrors `WebPushAdapter#send`: every attempt is logged to `push_notification_log` (type = payload
   `type`, status, success = 2xx, error message cut to 1000 chars); HTTP 404/410 delete the
   endpoint; an exception also deletes it. Without all three VAPID variables nothing is sent and
   `GET /push/public-key` returns an empty body (Java: warning at startup).
6. **Endpoints** (`/api/v2/push/*`, authenticated like all `/api/v2`): `public-key` returns the raw key
   labelled `application/json`; `subscribe` replaces a known endpoint of any user and answers
   204; `status?endpoint=` answers `{"exists": bool}`; `unsubscribe?endpoint=` deletes by endpoint (no
   owner check, as in Java) and answers 204. Missing endpoint/keys in `subscribe` yield 500 (Java NPE).
   `build_router` takes a `PushState` argument.
7. **Event bus fix.** `NOTIFY Name` folds the channel to lower case while `PgListener` listens on the
   quoted, case-preserved name, so no CamelCase event (including `AppointmentCreatedEvent`) reached a
   subscriber. `PostgresEventBus::fire` now uses `pg_notify($1, $2)`.

8. **Deliberate deviation: environment variable names.** Rust reads `VAPID_PUBLIC_KEY`,
   `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT`; Java reads `VAPID_PUBLIC`, `VAPID_PRIVATE`, `VAPID_MAILTO`
   (CLAUDE.md). The Rust names are prescribed by the ticket and describe the content (the value is
   the full VAPID subject claim, e.g. `mailto:...`, not just an address). Both backends are fed from
   the same Kubernetes secret keys (`vapid_public_key`, `vapid_private_key`, `vapid_mailto`), so
   operations maintain one set of secrets; only the container variable name differs
   (`deployment/values-staging.yaml`, `rustBackend.envFromSecrets`). Operating rule and behaviour
   without the variables: `kb/constraints/003-deployment-rules.md` (Secrets Management).

## Event -> publisher -> ticket

| Event | Publisher in Rust | Status |
|---|---|---|
| `AppointmentMovedEvent` | `AppointmentService::update_appointment` | done |
| `AppointmentCancelledEvent` | `AppointmentService::cancel_appointment` | done |
| `AppointmentParticipationAddedEvent` | `AppointmentService::add_participant` | done |
| `AppointmentParticipationStatusChangedEvent` | `AppointmentService::change_participation_status` (RSVP) | done |
| `AppointmentReminderEvent`, `AppointmentParticipationStatusPendingReminderEvent` | `ReminderService` | done (scheduler: #100) |
| `GroupMemberAddedEvent` | `GroupService` (payload `group_id`, `user_id`) | done |
| `AppointmentGroupParticipationAddedEvent` | not ported (group participants of appointments) | publisher must fire `{appointment_id, group_id, acting_user_id}` |
| `AppointmentParticipationInvalidEvent`, `AppointmentParticipationStatusRecheckRequestedEvent` | not ported (participation validity check) | publisher must fire `{appointment_id}` / `{appointment_id, acting_user_id}` |
| `MessageSentEvent` | messages (#124/#125) | publisher must fire `{message_id}` |
| `FriendshipRequestSentEvent` | friendships (#97) | `{request_id, requester_id, addressee_id, requester_name}` |
| `FriendshipAcceptedEvent`, `FriendshipDeclinedEvent` | friendships (#97) | `{request_id, requester_id, addressee_id}` |
| `FriendshipRemovedEvent` | friendships (#97) | `{acting_user_id, friend_id}` |

Events of not yet ported features are not invented; the listener handles them and is covered with
synthetic events.

## Consequences

- Every push event has an integration test (fake sender + database); the event bus wiring has one
  end-to-end test with `PostgresEventBus`.
- Known deviation: the `appointment` string uses the Rust REST serialisation (`start`/`end` RFC 3339
  as in the Rust appointment endpoints).
