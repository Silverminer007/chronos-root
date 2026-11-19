---
id: LS-002
title: Nuxt-Server-Routen als einziger Zugang zum Backend
status: entwurf
typ: strategie
quellen:
  - kb/adr/ADR-2025-09-28-010-server-routes-proxy.md
  - frontend/server/api/v2/[...path].ts
verknuepft: [RB-004, BB-002, KZ-007]
---

# Nuxt-Server-Routen als einziger Zugang zum Backend

Der Catch-all `/api/v2/[...path]` liest `kc_access` aus dem Cookie und leitet mit `Authorization: Bearer` an das Backend weiter. Tokens sind nie für JavaScript sichtbar, es gibt kein CORS, das Backend ist intern. Preis: ein zusätzlicher Hop und die Server-Rolle des Frontends.
