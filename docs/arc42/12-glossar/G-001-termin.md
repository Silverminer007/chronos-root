---
id: G-001
title: "Termin"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/Appointment.java
  - frontend/app/types/index.ts
verknuepft: [G-002, G-003]
---

# G-001: Termin

**Code-Name:** `Appointment` (Backend, Frontend, API `/api/v2/appointments`); Tabelle `appointment`

Geplante Veranstaltung mit `name`, `description`, `venue` (Ort), `startTime`/`endTime` (UTC-`Instant`, in der API `start`/`end`), [Terminstatus](G-002-terminstatus.md) und [Mindestteilnehmerzahl](G-049-mindestteilnehmerzahl.md) (`minimalAttendees`, API `minimal_attendees`). Hängt an [Teilnehmern](G-003-teilnehmer.md), [Gruppenteilnahmen](G-008-gruppenteilnahme.md) und [Nachrichten](G-011-nachricht-und-systemnachricht.md).
