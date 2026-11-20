---
id: BB-013
title: "Push-Benachrichtigungen (Ebene 2)"
status: entwurf
typ: baustein-ebene-2
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/WebPushService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/SettingsService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/WebPushAdapter.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/Settings.java
verknuepft: [KT-004, LZ-005, LZ-010, BB-003, FA-004, FA-012]
---

# Push-Benachrichtigungen (Ebene 2)

| Teil | Aufgabe |
|---|---|
| `WebPushService` | beobachtet Events ([BB-008](BB-008-events-und-ports.md)), bestimmt Empfänger, baut die JSON-Nutzlast |
| `SettingsService` | entscheidet je Empfänger anhand der Einstellung, ob gesendet wird |
| `NotificationPort` / `WebPushAdapter` | verschlüsselter Versand je Subscription, Protokoll ([KT-004](../03-kontext/KT-004-push-dienste.md)) |
| `PushSubscriptionService`, `PushResource` | Subscriptions speichern, prüfen, löschen (`POST /push/subscribe` ersetzt eine bekannte Subscription gleichen Endpunkts) |
| `AdminPushResource` | Testnachricht an einen Benutzer, Versandlog ([BB-015](BB-015-admin-bereich.md)) |

## Einstellungen

`Settings` je Benutzer mit einer Spalte je Ereignisart. `AppointmentNotificationSetting` (`DISABLED`, `ALL`, `ATTENDANT`, `HELPER`, `RESPONSIBLE`) wirkt als Mindestrolle des Empfängers am Termin (`ALL` ab `GUEST`); `group_member_added` ist `NotificationSetting` (`ENABLED`/`DISABLED`). Standardwerte setzt die Migration `V1.2.0`.

## Nutzlasten und Auslöser

Die Spalte „Einstellung“ nennt das Settings-Feld; „–“ heißt, dass keine Einstellung geprüft wird.

| `type` | Event | Empfänger | Einstellung |
|---|---|---|---|
| `APPOINTMENT_MOVED` | `AppointmentMoved` | Teilnehmer außer Auslöser | `appointment_moved` |
| `APPOINTMENT_CANCELLED` | `AppointmentCancelled` | Teilnehmer außer Auslöser | `appointment_cancelled` |
| `NEW_PARTICIPANT` | `…ParticipationAdded`, `…GroupParticipationAdded` (Feld `participant_type` `user` bzw. `group`) | Teilnehmer außer Auslöser | `appointment_participant_added` |
| `PARTICIPATION_STATUS_CHANGED` | `…StatusChanged` | Teilnehmer außer Auslöser | `appointment_participation_status_changed` |
| `APPOINTMENT_PARTICIPATION_INVALID` | `…ParticipationInvalid` | Teilnehmer | `appointment_participation_invalid` |
| `PARTICIPATION_STATUS_PENDING` | `…StatusPendingReminder` | Teilnehmer mit Status `PENDING` | `appointment_participation_status_pending` |
| `PARTICIPATION_REMINDER` | `AppointmentReminder` | Teilnehmer | `appointment_reminder` |
| `PARTICIPATION_STATUS_RECHECK` | `…StatusRecheckRequested` | Teilnehmer, immer | – |
| `NEW_GROUP_MEMBER` | `GroupMemberAdded` | übrige Gruppenmitglieder | `group_member_added` |
| `ADDED_TO_GROUP` | `GroupMemberAdded` | neues Mitglied | – |
| `NEW_FRIENDSHIP_REQUEST`, `FRIENDSHIP_ACCEPTED`, `FRIENDSHIP_DECLINED`, `FRIENDSHIP_REMOVED` | Freundschafts-Events | je der andere Beteiligte | – |
| ohne `type` (`title`, `body`) | `MessageSent` | Teilnehmer außer Absender | `appointment_message` |

Die Darstellung der Nutzlast übernimmt der Service Worker ([BB-003](BB-003-frontend-service-worker.md)); `SURVEY_INVITATION` und `GROUP_MEMBER_LEFT` kennt er, das Backend sendet sie nicht.
