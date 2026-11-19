---
id: KT-004
title: Nachbarsystem Web-Push-Dienste
status: entwurf
typ: nachbarsystem
quellen:
  - backend/.../infrastructure/WebPushAdapter.java
  - backend/.../config/PushConfig.java
  - frontend/public/push-sw.js
verknuepft: [FA-004, BB-013]
---

# Nachbarsystem Web-Push-Dienste

- **Rolle:** Browser-eigene Push-Dienste liefern Benachrichtigungen an Geräte (Web Push, VAPID).
- **Schnittstelle:** Das Backend sendet über `WebPushAdapter` (Java; Rust: Modul `push_notifications`) an die gespeicherte `PushSubscription`.
- **Konfiguration:** `VAPID_MAILTO`, `VAPID_PUBLIC`, `VAPID_PRIVATE`.
- **Endpunkte:** `/api/v2/push/{public-key,subscribe,status,unsubscribe}`.
- **Protokoll:** Jeder Versand wird in `PushNotificationLog` festgehalten (Migration `V1.6.0`).
