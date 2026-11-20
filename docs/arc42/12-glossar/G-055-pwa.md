---
id: G-055
title: "PWA (installierbare Web-App)"
status: entwurf
typ: begriff
quellen:
  - frontend/public/manifest.webmanifest
  - frontend/public/push-sw.js
  - frontend/nuxt.config.ts
verknuepft: [FA-016, BB-003, KT-009]
---

# G-055: PWA (installierbare Web-App)

**Code-Name:** `manifest.webmanifest`, Service Worker `push-sw.js`

Chronos ist eine Web-Anwendung, die der Browser per Manifest als eigenständige App auf dem Gerät installieren lässt und die über den Service Worker Push-Nachrichten empfängt und Tokens erneuert ([Service-Worker-Baustein](../05-bausteine/BB-003-frontend-service-worker.md)).
