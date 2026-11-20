---
id: LS-004
title: Token-Refresh im Service Worker
status: entwurf
typ: strategie
quellen:
  - frontend/public/push-sw.js
  - frontend/app/middleware/auth.global.ts
verknuepft: [AE-008, BB-003, LZ-002]
---

# Token-Refresh im Service Worker

Der Service Worker erneuert Tokens unabhängig vom Lebenszyklus der Komponenten, damit Nutzer keinen Token-Ablauf bemerken: proaktiv 30 s vor `kc_expires`, vor jeder `/api/*`-Anfrage außerhalb von `/api/auth/` bei weniger als 5 s Restlaufzeit und einmal nach einer 401-Antwort; parallele Refresh-Aufrufe teilen sich ein Promise. Die Router-Middleware prüft nur den Anmeldestatus. Ablauf: [LZ-002](../06-laufzeit/LZ-002-token-refresh.md); Begründung: [AE-008](../09-entscheidungen/AE-008-token-refresh-im-service-worker.md).
