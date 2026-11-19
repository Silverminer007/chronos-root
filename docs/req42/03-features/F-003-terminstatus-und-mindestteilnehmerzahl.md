---
id: F-003
title: Terminstatus und Mindestteilnehmerzahl
status: entwurf
typ: feature
quellen: [backend/.../domain/AppointmentStatus.java, kb/glossary/begriffe.md]
verknuepft: [Z-001, SH-002, SH-003, FA-003]
---

# F-003: Terminstatus und Mindestteilnehmerzahl

Ein Termin hat den Status PLANNED, CANCELLED, DELETED oder NOT_ENOUGH_ATTENDEES; bei zu wenigen Zusagen wird der Status entsprechend gesetzt.

## Umsetzung (Ist)
Enum `AppointmentStatus`, Feld `minimalAttendees`.

## Ziele
- Z-001

## Stakeholder
- SH-002
- SH-003

## Anforderungen
- FA-003
