---
id: R-003
title: "Abhängigkeit vom Service Worker für Token-Refresh"
status: entwurf
typ: risiko
quellen:
  - frontend/public/push-sw.js
  - frontend/app/plugins/register-sw.client.ts
verknuepft: [QA-004, AE-008, QS-006]
---

# R-003: Abhängigkeit vom Service Worker für Token-Refresh

**Risiko:** Die gesamte Token-Erneuerung ([AE-008](../09-entscheidungen/AE-008-token-refresh-im-service-worker.md)) und Push-Anzeige liegen in `push-sw.js`. Zwei Schwachstellen:
- Der Worker nutzt die Cookie-Store-API (`cookieStore`); fehlt sie im Browser, wirft jeder Aufruf, und es drohen 401-Fehler oder Abmeldungen. Welche Zielbrowser sie unterstützen, ist nicht festgelegt **(?)**.
- Der Worker lädt zur Laufzeit `luxon` per `importScripts` von `cdn.jsdelivr.net`. Ist das CDN nicht erreichbar, schlägt die Worker-Installation fehl; Refresh und Push entfallen.

**Auswirkung:** Nutzer:innen sehen Fehler oder landen auf der Startseite; Push bleibt aus.

**Maßnahmen:** keine automatische Prüfung ([QS-006](../10-qualitaet/QS-006-token-ablauf-ohne-sichtbaren-fehler.md)); `luxon` ließe sich lokal ausliefern.
