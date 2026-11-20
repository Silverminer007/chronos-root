---
id: G-039
title: "Service Worker"
status: entwurf
typ: begriff
quellen:
  - frontend/public/push-sw.js
verknuepft: [AE-008, AE-053, R-003]
---

# G-039: Service Worker

**Code-Name:** `push-sw.js` (registriert durch `register-sw.client.ts`)

Browser-Hintergrundskript der PWA mit zwei Aufgaben: Token-Erneuerung für alle `/api/*`-Anfragen ([AE-008](../09-entscheidungen/AE-008-token-refresh-im-service-worker.md)) und Anzeige eingehender [Push-Benachrichtigungen](G-013-push-benachrichtigung.md) mit Schnellaktionen.
