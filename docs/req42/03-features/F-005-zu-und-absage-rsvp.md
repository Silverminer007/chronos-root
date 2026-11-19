---
id: F-005
title: Zu- und Absage (RSVP)
status: entwurf
typ: feature
quellen: [backend/.../presentation/AppointmentParticipationResource.java, kb/adr/ADR-2026-10-04-012-rsvp-endpoint.md, kb/adr/ADR-2026-10-14-016-participation-status-endpoint.md]
verknuepft: [Z-001, Z-002, SH-003, SH-004, FA-003]
---

# F-005: Zu- und Absage (RSVP)

Teilnehmer antworten mit Zusage, Absage oder lassen die Antwort ausstehend (APPROVED, REJECTED, PENDING); der Status ist abfragbar.

## Umsetzung (Ist)
`PUT /appointments/{id}/participation`, `GET .../participation/status`.

## Ziele
- Z-001
- Z-002

## Stakeholder
- SH-003
- SH-004

## Anforderungen
- FA-003
