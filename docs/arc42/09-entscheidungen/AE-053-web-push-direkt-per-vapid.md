---
id: AE-053
title: "Web-Push direkt per VAPID"
status: entwurf
typ: architekturentscheidung
datum: 2026-04-02
quellen:
  - backend/pom.xml
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/WebPushAdapter.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/ports/NotificationPort.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/Settings.java
  - frontend/public/push-sw.js
verknuepft: [AE-008, AE-048, G-013, G-045, RB-006]
---

# AE-053: Web-Push direkt per VAPID

**Entscheidung:** Push-Nachrichten gehen über den W3C-Web-Push-Standard direkt an die Push-Dienste der Browser, signiert mit eigenen VAPID-Schlüsseln (`push.vapid.*`, Bibliothek `web-push` 5.1 mit BouncyCastle). `WebPushAdapter` implementiert `NotificationPort`; jeder Versand wird in `push_notification_log` protokolliert. Antwortet ein Push-Dienst mit 404 oder 410, löscht das Backend die Subscription. Fehlen die VAPID-Schlüssel, werden Pushes übersprungen (Warnung). Der Service Worker `push-sw.js` baut aus dem Payload Titel, Text und Schnellaktionen (Zusagen/Absagen). Was ein Benutzer erhält, steuert `Settings` je Ereignisart (`DISABLED`, `ALL`, `ATTENDANT`, `HELPER`, `RESPONSIBLE`).

**Begründung:** Keine Abhängigkeit von einem proprietären Push-Anbieter und keine Gerätetoken-Verwaltung; funktioniert für die PWA ohne native App (Begründung nicht dokumentiert **(?)**).

**Alternativen:** FCM/APNs über Anbieter-SDK; E-Mail.

**Konsequenzen:** Zustellung hängt von Browser und Betriebssystem (insbesondere iOS-PWA-Installation, Seite `public/install`); Subscriptions veralten und werden erst beim Versand bereinigt; das Backend verwaltet VAPID-Geheimnisse ([AE-014](AE-014-austauschbarer-secret-provider-im-helm-chart.md)).
