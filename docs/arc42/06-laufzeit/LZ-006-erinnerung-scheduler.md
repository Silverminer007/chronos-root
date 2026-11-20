---
id: LZ-006
title: Erinnerungen, Scheduler und Leader-Election
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/scheduler
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentReminderService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/ReminderRuleEngine.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationStatusConfirmationService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationValidationService.java
verknuepft: [BB-012, LS-012, FA-004, FA-011, LZ-011]
---

# Erinnerungen, Scheduler und Leader-Election

Alle 15 Minuten (Minute 0, 15, 30, 45) starten die drei Zeitaufgaben aus [BB-012](../05-bausteine/BB-012-java-reminder.md) auf jeder Instanz; wirksam ist nur der Leader (`isLeader()`).

Erinnerung an ausstehende Rückmeldungen:
1. `AppointmentReminderService` lädt Termine mit Start nach jetzt und vor jetzt plus 20 Wochen, deren Status weder `CANCELLED` noch `DELETED` ist.
2. Die Engine wählt je Termin die erste zutreffende Regel und prüft ihre Auslösezeitpunkte der Reihe nach.
3. Fällig ist ein Zeitpunkt, wenn er nicht in der Zukunft liegt und höchstens 15 volle Minuten zurückliegt; beim ersten fälligen Zeitpunkt feuert die Regel (`sendRSVPReminder`) und die Engine geht zum nächsten Termin.
4. `AppointmentParticipationStatusPendingReminderEvent` → Push `PARTICIPATION_STATUS_PENDING` an Teilnehmer mit Status `PENDING` und passender Einstellung.

Die Zeitfenster-Prüfung hat eine Randeigenschaft: Liegt ein Zeitpunkt exakt auf einem Cron-Tick (typisch bei Terminen, die zur Viertelstunde beginnen), ist er im Lauf des Ticks und im Folgelauf (Abstand 15 Minuten und einige Millisekunden, abgeschnitten 15 Minuten) fällig; die Erinnerung wird dann doppelt gesendet, weil nirgends gespeichert wird, dass sie schon gesendet wurde.

Die Mindestteilnehmer-Prüfung und die Bestätigungsereignisse laufen im selben Takt ([LZ-011](LZ-011-mindestteilnehmerzahl.md)); `…StatusConfirmation` wird nur ins Debug-Log geschrieben.

Leader-Wechsel: Jede Instanz bewirbt sich um das Lease `chronos-scheduler`; fällt der Leader aus, übernimmt eine andere nach Ablauf der Lease-Dauer (15 s), spätestens zum nächsten Tick. `LEADER_ELECTION_ENABLED=false` macht jede Instanz zum Leader, mehrere Instanzen würden dann doppelt senden.
