---
id: TD-013
title: "Doppelte Erinnerung bei Terminen auf dem Cron-Tick"
status: entwurf
typ: technische-schuld
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/ReminderRuleEngine.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/scheduler/AppointmentReminderScheduler.java
verknuepft: [AE-052, G-010, QS-010]
---

# TD-013: Doppelte Erinnerung bei Terminen auf dem Cron-Tick

`ReminderRuleEngine.isDue` gilt ein Triggerzeitpunkt als fällig, wenn `Duration.between(trigger, now).toMinutes() <= 15`; der Scheduler läuft alle 15 min (`0 */15 * * * ?`). `toMinutes()` schneidet ab: Liegt der Trigger weniger als eine Minute vor einem Tick (typisch bei Terminen, die auf :00, :15, :30 oder :45 beginnen, da Trigger Start minus ganze Tage oder Wochen sind), ist er im Tick und im folgenden Tick fällig, weil die Differenz dort 15 min plus Bruchteil beträgt. Eine Dedupe-Protokollierung gibt es nicht.

**Auswirkung:** Teilnehmer erhalten die Rückmelde-Erinnerung doppelt ([QS-010](../10-qualitaet/QS-010-genau-ein-scheduler-trotz-replicas.md) fordert höchstens einmal; vgl. [QA-008](../../req42/06-qualitaet/QA-008-erinnerungen-genau-einmal-bei-mehreren-instanzen.md)). Nicht in Produktion beobachtet **(?)**.

**Maßnahme:** Fenster halboffen prüfen (`diff < 15 min`, mit Millisekunden-Genauigkeit) und einen Test mit Triggern auf dem Tick ergänzen.
