---
id: RA-007
title: "Frontend nutzt nur Nuxt-Server-Routen"
status: entwurf
typ: randbedingung
quellen:
  - CLAUDE.md
  - kb/constraints/C2025-09-28-005-no-direct-backend-calls.md
verknuepft: [RB-004]
---

# RA-007: Frontend nutzt nur Nuxt-Server-Routen

Alle API-Aufrufe laufen über `server/api/v2/[...path].ts` an das Backend; der Client kennt die Backend-URL nicht. Siehe RB-004 in req42.
