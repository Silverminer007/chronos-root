---
id: F-001
title: Termine verwalten
status: entwurf
typ: feature
quellen: [backend/.../presentation/AppointmentsResource.java, frontend/app/components/CreateAppointmentStepper.vue, kb/adr/ADR-2026-10-03-011-appointment-mutations.md]
verknuepft: [Z-001, SH-002, FA-001, FA-008]
---

# F-001: Termine verwalten

Termine mit Name, Zeitraum, Ort und Mindestteilnehmerzahl anlegen, ändern, verschieben, absagen und löschen (Soft-Delete über Status).

## Umsetzung (Ist)
`POST/PATCH/DELETE /api/v2/appointments`, `POST /{id}/cancel`; Frontend: Erstellen-Stepper, Bearbeiten-Sheet, Detailseite.

## Ziele
- Z-001

## Stakeholder
- SH-002

## Anforderungen
- FA-001
- FA-008
