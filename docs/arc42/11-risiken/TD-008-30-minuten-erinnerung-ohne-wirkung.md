---
id: TD-008
title: "30-Minuten-Erinnerung ohne Wirkung"
status: entwurf
typ: technische-schuld
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/AppointmentReminderRule.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/ReminderRuleEngine.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/ports/ReminderEventCDIAdapter.java
verknuepft: [AE-052, G-010]
---

# TD-008: 30-Minuten-Erinnerung ohne Wirkung

`ReminderEventCDIAdapter.sendReminder` hat einen leeren Rumpf; `AppointmentReminderRule` (30 min vor Beginn, gilt für alle Termine) ruft ihn auf. `AppointmentReminderEvent` wird daher nirgends ausgelöst, obwohl `WebPushService` ihn beobachtet; die Erinnerung „Termin steht bald an" entsteht nicht. Zusätzlich wählt `ReminderRuleEngine` je Termin nur die **erste** passende Regel, und `AppointmentReminderRule` passt auf jeden Termin; ob die Rückmelde-Regeln vorher geprüft werden, hängt von der Injektionsreihenfolge ab, die nicht festgelegt ist (keine `@Priority` an den Regeln); ob in Produktion die Rückmelde-Regeln greifen, ist aus dem Code nicht zu entscheiden **(?)**.

**Auswirkung:** Die Erinnerung zum Termin fehlt; im ungünstigen Fall verdrängt `AppointmentReminderRule` die Rückmelde-Regeln ganz.

**Maßnahme:** `sendReminder` implementieren (Observer `WebPushService` bedienen), Regelreihenfolge explizit festlegen (`@Priority`) oder je Termin alle zutreffenden Regeln auswerten. Siehe auch RA-005.
