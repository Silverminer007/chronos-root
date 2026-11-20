---
id: G-049
title: "Mindestteilnehmerzahl"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationValidationService.java
verknuepft: [G-002, G-005, AE-052, AE-056, TD-012]
---

# G-049: Mindestteilnehmerzahl

**Code-Name:** `minimalAttendees` (API `minimal_attendees`); Push-Typ `APPOINTMENT_PARTICIPATION_INVALID`

Anzahl der Zusagen, die ein [Termin](G-001-termin.md) braucht, um stattzufinden. Es zählen zunächst alle, die nicht abgesagt haben (Teilnehmer minus `REJECTED`); innerhalb der Rückmeldefrist (8 Wochen vor langen, 1 Woche vor Terminen mit Beginn Mo bis Do, 2 Wochen vor Terminen mit Beginn Fr bis So, jeweils UTC) muss die Zahl der Zusagen (`APPROVED`) ausreichen. Wird sie unterschritten, wechselt der Termin auf `NOT_ENOUGH_ATTENDEES` ([G-002](G-002-terminstatus.md)) und die Teilnehmer erhalten (nach ihren Einstellungen) eine Push-Nachricht.
