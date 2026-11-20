---
id: G-012
title: "Verschiebung"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/MessageService.java
verknuepft: [G-011, G-013]
---

# G-012: Verschiebung

**Code-Name:** `AppointmentMovedEvent`; Push-Typ `APPOINTMENT_MOVED`; Einstellung `appointment_moved`

Änderung von Start oder Ende eines [Termins](G-001-termin.md) über `PATCH /api/v2/appointments/{id}`. Löst eine [Push-Benachrichtigung](G-013-push-benachrichtigung.md) und eine [Systemnachricht](G-011-nachricht-und-systemnachricht.md) aus („um 2 Stunden nach hinten verschoben" bzw. „vorverlegt"; Beginn hat Vorrang vor Ende; Änderungen unter einer Minute erzeugen keine Nachricht).
