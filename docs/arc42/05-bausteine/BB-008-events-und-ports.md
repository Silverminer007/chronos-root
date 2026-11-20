---
id: BB-008
title: "Java-Backend: Events und Ports (Ebene 2)"
status: entwurf
typ: baustein-ebene-2
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/events
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/ports
  - backend/src/main/java/de/chronos_live/chronos_date_api/application
verknuepft: [KZ-003, LS-005, LS-006, BB-013]
---

# Java-Backend: Events und Ports (Ebene 2)

## Events

26 Java-`record`s in `application/events`, ausgelöst von den Services. Technik und Regeln: [KZ-003](../08-konzepte/KZ-003-events-und-event-bus.md).

| Thema | Events | Beobachter |
|---|---|---|
| Termin | `AppointmentCreated` | `AppointmentParticipationService` (Ersteller wird `RESPONSIBLE`, Status `PENDING`) |
| | `AppointmentEdited` | `AppointmentParticipationValidationService` (Mindestteilnehmerzahl neu prüfen) |
| | `AppointmentMoved` | `MessageService` (Systemnachricht), `WebPushService` |
| | `AppointmentCancelled` | `MessageService` (Systemnachricht), `WebPushService` |
| | `AppointmentDeleted` | keiner |
| Teilnahme | `AppointmentParticipationAdded` | `WebPushService` |
| | `AppointmentParticipationRemoved`, `…RoleChanged` | keiner (nur ausgelöst) |
| | `AppointmentParticipationStatusChanged` | `MessageService`, `WebPushService`, `AppointmentParticipationValidationService` (asynchron) |
| | `AppointmentParticipationInvalid` | `WebPushService` |
| | `AppointmentParticipationStatusPendingReminder` | `WebPushService` |
| | `AppointmentParticipationStatusConfirmation` | nur `DebugLoggingService` |
| | `AppointmentParticipationStatusRecheckRequested` | `WebPushService`; im Code nirgends ausgelöst |
| Gruppenteilnahme | `AppointmentGroupParticipationAdded` | `WebPushService` |
| | `AppointmentGroupParticipationRemoved` | keiner |
| Freundschaft | `FriendshipRequestSent`, `Accepted`, `Declined`, `Removed` | `WebPushService` |
| Gruppe | `GroupCreated` | `GroupService` (Ersteller wird Mitglied) |
| | `GroupMemberAdded` | `AppointmentParticipationService` (Teilnahme in Terminen der Gruppe), `WebPushService` |
| | `GroupMemberRemoved` | `AppointmentParticipationService` (Teilnahmen der Gruppe entfernen) |
| | `GroupDeleted`, `GroupNameChanged` | keiner |
| Nachricht | `MessageSent` | `WebPushService` |
| Erinnerung | `AppointmentReminder` | `WebPushService`; im Code nirgends ausgelöst |

## Ports

| Port | Aufgabe | Adapter |
|---|---|---|
| `IdentityPort` | Benutzer per `oidcId` auflösen, Profil-Cache schreiben, suchen | `LocalDbIdentityAdapter` ([KZ-012](../08-konzepte/KZ-012-identitaet-und-profil-cache.md)) |
| `NotificationPort` | Nutzlast an alle Subscriptions eines Benutzers senden, VAPID-Public-Key | `WebPushAdapter` |
| `NotificationLogPort` | Sendeversuch protokollieren | `PushNotificationLogRepository` |
| `ReminderEventPort` | `sendReminder`, `sendRSVPReminder` | `ReminderEventCDIAdapter` (`sendReminder` ist leer, `sendRSVPReminder` löst `…StatusPendingReminder` aus) |
