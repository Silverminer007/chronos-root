---
id: F-006
title: Gruppen verwalten
status: entwurf
typ: feature
quellen: [backend/.../presentation/GroupsResource.java, frontend/app/pages/groups/, kb/adr/ADR-2026-10-20-019-groups-friendships-endpoints.md]
verknuepft: [Z-003, SH-002, SH-003, FA-006]
---

# F-006: Gruppen verwalten

Benutzer legen Gruppen an, benennen sie um, löschen sie und verwalten Mitglieder.

## Umsetzung (Ist)
`/api/v2/groups` (GET, POST, PATCH, DELETE), `/{group}/users`, `/{groupId}/user/{userId}`; Seiten `groups/`.

## Ziele
- Z-003

## Stakeholder
- SH-002
- SH-003

## Anforderungen
- FA-006
