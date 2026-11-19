---
id: R-003
title: "Abhängigkeit vom Service Worker für Token-Refresh"
status: entwurf
typ: risiko
quellen:
  - CLAUDE.md
  - kb/adr/ADR-2025-09-28-005-service-worker.md
verknuepft: [QA-004]
---

# R-003: Abhängigkeit vom Service Worker für Token-Refresh

Der Token-Refresh liegt im Service Worker (`push-sw.js`, nutzt `cookieStore`). Fällt der SW aus oder fehlt `cookieStore` im Browser, drohen 401-Fehler und Sitzungsabbrüche. Automatische Tests dafür fehlen (QS-006) **(?)**.
