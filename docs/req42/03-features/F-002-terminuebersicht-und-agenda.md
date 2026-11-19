---
id: F-002
title: Terminübersicht und Agenda
status: entwurf
typ: feature
quellen: [frontend/app/pages/agenda.vue, kb/adr/ADR-2026-10-20-026-appointment-list-and-embedded-details.md]
verknuepft: [Z-001, SH-002, SH-003, SH-004, FA-009]
---

# F-002: Terminübersicht und Agenda

Paginierte Liste der eigenen Termine mit eingebetteten Teilnehmern und Nachrichten; Agenda-Seite im Frontend.

## Umsetzung (Ist)
`GET /api/v2/appointments`, `GET /{id}`; Seite `agenda.vue`.

## Ziele
- Z-001

## Stakeholder
- SH-002
- SH-003
- SH-004

## Anforderungen
- FA-009
