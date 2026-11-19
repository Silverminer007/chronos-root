---
id: F-004
title: Teilnehmer einladen, entfernen und Rollen ändern
status: entwurf
typ: feature
quellen: [backend/.../presentation/AppointmentParticipationResource.java, kb/adr/ADR-2026-10-04-001-add-participant-endpoint.md, kb/adr/ADR-2026-10-20-020-change-participant-role.md]
verknuepft: [Z-001, Z-003, SH-002, FA-002, FA-010]
---

# F-004: Teilnehmer einladen, entfernen und Rollen ändern

Verantwortliche laden einzelne Personen ein, entfernen sie wieder und ändern deren Rolle (NONE, GUEST, ATTENDANT, HELPER, RESPONSIBLE).

## Umsetzung (Ist)
`POST/DELETE /appointments/{id}/participants/{userId}`, `PUT .../participation/{userId}/role`, `GET .../participants`.

## Ziele
- Z-001
- Z-003

## Stakeholder
- SH-002

## Anforderungen
- FA-002
- FA-010
