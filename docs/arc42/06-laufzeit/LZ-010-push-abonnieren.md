---
id: LZ-010
title: Push abonnieren
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/.../PushResource.java
  - frontend/public/push-sw.js
verknuepft: [KT-004, FA-004]
---

# Push abonnieren

Der Client holt `GET /api/v2/push/public-key`, registriert den Service Worker, abonniert und sendet `POST /api/v2/push/subscribe`; `GET /push/status` zeigt den Zustand, `DELETE /push/unsubscribe` beendet ihn. Ab dann liefert `WebPushService` Nachrichten an die gespeicherte Subscription.
