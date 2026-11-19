---
id: LS-004
title: Token-Refresh im Service Worker
status: entwurf
typ: strategie
quellen:
  - kb/adr/ADR-2025-09-28-005-service-worker.md
  - frontend/public/push-sw.js
verknuepft: [BB-003, LZ-002]
---

# Token-Refresh im Service Worker

Der Service Worker erneuert Tokens unabhängig vom Komponenten-Lebenszyklus: proaktiv 30 s vor `kc_expires`, vor jeder `/api/v2/*`-Anfrage bei < 5 s Restlaufzeit und einmal nach einem 401. Parallele Refresh-Aufrufe teilen sich ein Promise (`inflightRefresh`). Die Middleware prüft nur den Login-Status.
