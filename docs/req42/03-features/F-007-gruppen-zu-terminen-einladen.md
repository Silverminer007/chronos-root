---
id: F-007
title: Gruppen zu Terminen einladen
status: entwurf
typ: feature
quellen: [backend/.../presentation/AppointmentParticipationResource.java, kb/adr/ADR-2026-10-05-012-appointment-groups-add-group.md, kb/adr/ADR-2026-10-20-024-appointment-remove-group.md]
verknuepft: [Z-003, Z-001, SH-002, FA-002]
---

# F-007: Gruppen zu Terminen einladen

Eine ganze Gruppe wird zu einem Termin eingeladen oder wieder entfernt; Mitglieder erhalten eigene Teilnahmen mit Bezug zur Gruppenteilnahme.

## Umsetzung (Ist)
`POST/DELETE /appointments/{id}/groups/{groupId}`.

## Ziele
- Z-003
- Z-001

## Stakeholder
- SH-002

## Anforderungen
- FA-002
