---
id: BB-013
title: Push-Benachrichtigungen (Ebene 2)
status: entwurf
typ: baustein-ebene-2
quellen:
  - backend/.../application/WebPushService.java
  - backend/.../infrastructure/WebPushAdapter.java
  - rust-backend/src/push_notifications
verknuepft: [KT-004, LZ-005, FA-004]
---

# Push-Benachrichtigungen (Ebene 2)

`WebPushService` beobachtet Events, löst Empfänger und Text auf, respektiert `NotificationSetting` (`ENABLED`/`DISABLED`, je Ereignisart) und sendet über `WebPushAdapter`; jeder Versand landet in `PushNotificationLog`. Rust: Modul `push_notifications` mit `events.rs`, `services`, `repository.rs`. Verwaltung über `/api/v2/push/*` und Admin-Push-Seite.
