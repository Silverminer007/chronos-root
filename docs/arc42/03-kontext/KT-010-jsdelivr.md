---
id: KT-010
title: Nachbarsystem jsDelivr (CDN)
status: entwurf
typ: nachbarsystem
quellen:
  - frontend/public/push-sw.js
  - frontend/nuxt.config.ts
verknuepft: [BB-003]
---

# Nachbarsystem jsDelivr (CDN)

Der Service Worker lädt beim Start per `importScripts` `luxon@3.4.4` von `cdn.jsdelivr.net`, um Zeiträume in Push-Texten zu formatieren. `importScripts` ist synchron: Ist das CDN beim ersten Installieren nicht erreichbar, scheitert die Service-Worker-Registrierung, und Push-Anzeige und Token-Refresh ([LS-004](../04-loesungsstrategie/LS-004-service-worker-token-refresh.md)) entfallen; ein bereits installierter Worker behält das Skript im Browser-Cache. Die Schrift Inter lädt der Build dagegen herunter und liefert sie selbst aus (`googleFonts.download: true`).
