---
id: FA-007
title: Admin-API
status: entwurf
typ: anforderung
quellen: [kb/adr/ADR-2026-10-20-023-admin-endpoints.md]
verknuepft: [F-015]
---

# FA-007: Admin-API

Administratoren haben einen eigenen API-Zugang (`ADMIN_API`).

## Akzeptanzkriterien
- Zugriff ohne Rolle `ADMIN_API` wird mit 403 abgelehnt, ohne Anmeldung mit 401.
- Admin-Endpunkte umgehen die Teilnehmerprüfungen der normalen Endpunkte.

## Features
- F-015

## Priorität
**(?)** vom Product Owner festzulegen.
