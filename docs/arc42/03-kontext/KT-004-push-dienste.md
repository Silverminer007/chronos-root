---
id: KT-004
title: Nachbarsystem Web-Push-Dienste
status: entwurf
typ: nachbarsystem
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/WebPushAdapter.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/config/PushConfig.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/presentation/PushResource.java
verknuepft: [BB-013, LZ-005, LZ-010, RB-006]
---

# Nachbarsystem Web-Push-Dienste

- **Rolle:** Die Push-Dienste der Browserhersteller stellen Benachrichtigungen an abonnierte Geräte zu (Web Push mit VAPID, RB-006).
- **Schnittstelle:** `WebPushAdapter` (Bibliothek `web-push`, BouncyCastle-Provider) sendet die Nutzlast verschlüsselt an `endpoint`, `p256dh` und `auth` jeder gespeicherten `PushSubscription` des Empfängers. Antwort 404 oder 410 löscht die Subscription; jede Ausnahme beim Senden löscht sie ebenfalls und wird protokolliert. Jeder Sendeversuch je Subscription landet in `PushNotificationLog`.
- **Konfiguration:** `VAPID_MAILTO`, `VAPID_PUBLIC`, `VAPID_PRIVATE` (`push.vapid.*`). Fehlt eines, ist der Versand abgeschaltet und das Backend warnt beim Start und bei jedem Sendeversuch.
- **Endpunkte der Anwendung:** `GET /api/v2/push/public-key`, `POST …/subscribe`, `GET …/status`, `DELETE …/unsubscribe` ([LZ-010](../06-laufzeit/LZ-010-push-abonnieren.md)).
