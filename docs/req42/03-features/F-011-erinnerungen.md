---
id: F-011
title: Erinnerungen
status: entwurf
typ: feature
quellen: [backend/.../application/reminder/, kb/adr/ADR-2026-10-20-022-reminder-engine-scheduler-leader-election.md]
verknuepft: [Z-002, SH-003, SH-004, FA-011]
---

# F-011: Erinnerungen

Zeitgesteuerte Erinnerungen über eine Regel-Engine: Terminerinnerung 30 Minuten vor Beginn sowie Rückmelde-Erinnerungen, gestaffelt nach Dauer (unter/ab 24 Stunden) und Wochentag/Wochenende.

## Umsetzung (Ist)
`ReminderRule`-Implementierungen, `AppointmentReminderScheduler` mit Leader-Election.

## Ziele
- Z-002

## Stakeholder
- SH-003
- SH-004

## Anforderungen
- FA-011
