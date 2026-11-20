---
id: G-013
title: "Push-Benachrichtigung"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/PushSubscription.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/Settings.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/AppointmentNotificationSetting.java
verknuepft: [AE-053, G-045]
---

# G-013: Push-Benachrichtigung

**Code-Name:** `PushSubscription` (je Gerät: `endpoint`, `auth`, `p256dh`, `userOidcId`); Einstellungen `Settings` mit `AppointmentNotificationSetting` (`DISABLED`, `ALL`, `ATTENDANT`, `HELPER`, `RESPONSIBLE`) und `NotificationSetting` (`DISABLED`, `ENABLED`); Frontend `NotificationSettings`

Web-Push an die Geräte eines Benutzers. Je Ereignisart (verschoben, Nachricht, abgesagt, Teilnehmer hinzugefügt, Status geändert, zu wenige Teilnehmer, Rückmeldung fehlt, Erinnerung, Gruppenmitglied hinzugefügt) legt der Benutzer fest, ob und ab welcher [Rolle](G-004-rolle-teilnehmer.md) er sie erhält.
