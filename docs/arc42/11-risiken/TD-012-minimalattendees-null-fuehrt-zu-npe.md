---
id: TD-012
title: "Fehlende Mindestteilnehmerzahl führt zu NullPointerException"
status: entwurf
typ: technische-schuld
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/Appointment.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationValidationService.java
  - backend/src/main/resources/db/migration/V1.0.0__initial_schema.sql
verknuepft: [G-049, AE-048, AE-052, QA-008]
---

# TD-012: Fehlende Mindestteilnehmerzahl führt zu NullPointerException

`Appointment.minimalAttendees` ist ein nullbarer `Integer` (Spalte `minimalattendees INTEGER` ohne `NOT NULL`, API-Feld `minimal_attendees` optional). `AppointmentParticipationValidationService.onAppointmentEdited` prüft auf `null`, aber `checkForEnoughAttendees` (Scheduler `sendNonMinimalAttendeesAlerts`) und der Beobachter `onAppointmentParticipationStatusChanged` entpacken den Wert ohne Prüfung. Bei einem Termin ohne Mindestteilnehmerzahl wirft jeder Vergleich eine `NullPointerException`.

**Auswirkung:** Im Beobachter bleibt eine Zu-/Absage ohne Folgeprüfung und Fehler im Log (der Beobachter läuft asynchron, siehe [R-010](R-010-asynchrone-beobachter-der-zu-absage-laufen-vor-dem-commit.md)); im Scheduler kann die Ausnahme den Lauf für weitere Termine des Fensters abbrechen. Ob die Oberfläche je einen Termin ohne Wert anlegt, ist nicht geprüft **(?)**.

**Maßnahme:** `null` an den Vergleichsstellen wie in `onAppointmentEdited` behandeln, oder den Wert verpflichtend machen (Migration mit Standardwert).
