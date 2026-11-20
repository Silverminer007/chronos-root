---
id: FA-011
title: Automatische Erinnerungen
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/, backend/src/main/java/de/chronos_live/chronos_date_api/application/scheduler/AppointmentReminderScheduler.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/ports/ReminderEventCDIAdapter.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationStatusConfirmationService.java]
verknuepft: [F-011]
---

# FA-011: Automatische Erinnerungen

*Als Verantwortliche/r möchte ich, dass ausstehende Rückmeldungen automatisch angemahnt werden, damit ich nicht nachfragen muss.*

## Akzeptanzkriterien
- Erinnert wird per Benachrichtigung „Rückmeldung ausstehend“ an Teilnehmer mit Status `PENDING`, sofern ihre Einstellung es zulässt ([M-007](../../05-modelle/M-007-benachrichtigungsmatrix.md)); abgesagte und gelöschte Termine erhalten keine Erinnerungen.
- Der Zeitplan richtet sich nach Dauer und Starttag (Werktag Mo–Do, Wochenende Fr–So, in UTC):

| Termin | Erinnerung vor Beginn |
|---|---|
| Dauer ab 24 Stunden | 16, 8, 4 und 2 Wochen, danach täglich |
| unter 24 Stunden, Start Mo–Do | 7, 4, 2 und 1 Tag |
| unter 24 Stunden, Start Fr–So | 4, 2 und 1 Woche, danach täglich |

- Je Lauf (alle 15 Minuten) und Termin wird höchstens eine Erinnerung ausgelöst, nur für Termine innerhalb der nächsten 20 Wochen.
- Laufen mehrere Backend-Instanzen, wird jede Erinnerung nur einmal versendet ([QA-008](../../06-qualitaet/QA-008-erinnerungen-genau-einmal-bei-mehreren-instanzen.md)).
- Die Terminerinnerung 30 Minuten vor Beginn (Einstellung „Terminerinnerung“) ist als Regel angelegt, bleibt aber ohne Wirkung ([RA-005](../../12-risiken-annahmen/RA-005-erinnerungen-teilweise-ohne-wirkung.md)).
