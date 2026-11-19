---
id: RB-004
title: Frontend ruft das Backend nie direkt auf
status: entwurf
typ: randbedingung
quellen: [kb/constraints/C2025-09-28-005-no-direct-backend-calls.md]
verknuepft: []
---

# RB-004: Frontend ruft das Backend nie direkt auf

Alle Aufrufe laufen über die Nuxt-Server-Routen unter `/api/v2/*`; das Backend ist intern und nicht für den Browser erreichbar.

## Herkunft
Bisher: C2025-09-28-005.
