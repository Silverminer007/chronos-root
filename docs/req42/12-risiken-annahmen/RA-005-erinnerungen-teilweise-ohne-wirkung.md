---
id: RA-005
title: Erinnerungen teilweise ohne Wirkung
status: entwurf
typ: risiko
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/ports/ReminderEventCDIAdapter.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/ReminderRuleEngine.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationStatusConfirmationService.java]
verknuepft: [Z-002, FA-011]
---

# RA-005: Erinnerungen teilweise ohne Wirkung

**Risiko:** (1) Die Terminerinnerung 30 Minuten vor Beginn bleibt ohne Wirkung: Die Regel wird nur geprüft, wenn keine andere Regel zutrifft (jeder Termin fällt unter eine Rückmelde-Regel), und der Versand `sendReminder` ist leer. Die Einstellung „Terminerinnerung“ wirkt deshalb nicht. (2) Die Rückfrage „Teilnahme noch gültig?“ (30 Tage vorher bei Terminen ab 24 Stunden, 7 Tage vorher bei Start Fr–So) wird nur protokolliert, es wird nichts versendet. Nutzer erwarten laut Einstellungsseite eine Terminerinnerung.

**Wahrscheinlichkeit / Schaden:** Eintritt sicher, Schaden mittel (verpasste Termine). **Maßnahme:** Erinnerung umsetzen oder aus der Oberfläche entfernen; Product Owner entscheidet.
