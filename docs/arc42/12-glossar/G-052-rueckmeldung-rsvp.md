---
id: G-052
title: "Rückmeldung (RSVP)"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/ports/ReminderEventCDIAdapter.java
verknuepft: [G-005, G-010, G-049]
---

# G-052: Rückmeldung (RSVP)

**Code-Name:** `RSVP` in `ShortWeekdayRSVPRule`, `ShortWeekendRSVPRule`, `LongAppointmentRSVPRule`, `ReminderEventPort.sendRSVPReminder`; Ereignis `AppointmentParticipationStatusPendingReminderEvent`

Die Antwort eines [Teilnehmers](G-003-teilnehmer.md) auf die Einladung, also [Zusage oder Absage](G-005-zusage-absage.md). Eine **Rückmelde-Erinnerung** mahnt Teilnehmer mit Status `PENDING` an die fehlende Antwort ([Erinnerung](G-010-erinnerung.md)); die Frist für die Rückmeldung bestimmt zugleich die Prüfung der [Mindestteilnehmerzahl](G-049-mindestteilnehmerzahl.md).
