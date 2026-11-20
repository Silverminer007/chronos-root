---
id: LZ-010
title: Push abonnieren
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/presentation/PushResource.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/PushSubscriptionService.java
  - frontend/app/composables/usePush.ts
verknuepft: [KT-004, FA-004, RB-006]
---

# Push abonnieren

1. Das Layout `default` blendet `PushPermissionModal` ein, wenn `usePush.shouldAsk()` zutrifft: Push wird vom Browser unterstützt, die Berechtigung ist nicht abgelehnt, die gespeicherte Antwort (`pushPromptAnswer`) ist nicht `denied`, und es wurde noch nie oder vor mehr als 7 Tagen (`pushPromptShownAt`) gefragt.
2. Nach Zustimmung holt der Client `GET /api/v2/push/public-key`, wartet auf den Service Worker, abonniert über `pushManager.subscribe` und sendet `POST /api/v2/push/subscribe` mit `endpoint` und `keys` (`p256dh`, `auth`). Eine bereits bekannte Subscription gleichen Endpunkts ersetzt der Server.
3. Bei späteren Besuchen prüft der Client mit `GET /push/status?endpoint=…`, ob der Server das Abo kennt, und abonniert andernfalls neu. `DELETE /push/unsubscribe?endpoint=…` entfernt ein Abo; der Server löscht ungültige Subscriptions außerdem selbst ([KT-004](../03-kontext/KT-004-push-dienste.md)).
4. Ab dann stellt `WebPushService` Nachrichten zu ([LZ-005](LZ-005-verschiebung-mit-push.md)).
