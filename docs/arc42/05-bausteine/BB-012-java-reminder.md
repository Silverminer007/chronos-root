---
id: BB-012
title: "Java-Backend: Zeitsteuerung, Reminder-Engine und Leader-Election (Ebene 2)"
status: entwurf
typ: baustein-ebene-2
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/scheduler
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/LeaderElectionService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationValidationService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationStatusConfirmationService.java
  - backend/src/main/resources/application.properties
verknuepft: [LS-012, LZ-006, FA-004, FA-011]
---

# Java-Backend: Zeitsteuerung, Reminder-Engine und Leader-Election (Ebene 2)

## Zeitgesteuerte Aufgaben

Drei `@Scheduled`-Methoden mit Cron `0 */15 * * * ?` (alle 15 Minuten), jeweils nur wirksam, wenn `LeaderElectionService.isLeader()` gilt:

| Methode | Zweck |
|---|---|
| `AppointmentReminderScheduler.triggerPendingReminders` → `AppointmentReminderService` → `ReminderRuleEngine` | Erinnerungen an ausstehende Rückmeldungen |
| `AppointmentParticipationValidationService.sendNonMinimalAttendeesAlerts` | Status `NOT_ENOUGH_ATTENDEES` bei zu wenigen Zusagen zu festen Fristen |
| `AppointmentParticipationStatusConfirmationService.triggerAppointmentParticipationStatusConfirmation` | löst `…StatusConfirmation` aus (30 Tage vor langen Terminen, 7 Tage vor Terminen Fr–So) |

## Regeln der Reminder-Engine

`RuleConfig` erzeugt die Regelliste in dieser Reihenfolge; je Termin gilt die erste Regel mit `appliesTo`. Zeiten in UTC, „Dauer“ = Start bis Ende in vollen Stunden, Wochentag nach Starttag (`AppointmentTimeUtil`).

| Regel | Gilt für | Auslösezeitpunkte (vor dem Start) | Aktion |
|---|---|---|---|
| `LongAppointmentRSVPRule` | Dauer ≥ 24 h | 16, 8, 4, 2 Wochen; danach täglich zur Startzeit bis zum Start | `sendRSVPReminder` |
| `ShortWeekdayRSVPRule` | Dauer < 24 h, Start Mo–Do | 7, 4, 2, 1 Tag | `sendRSVPReminder` |
| `ShortWeekendRSVPRule` | Dauer < 24 h, Start Fr–So | 4, 2, 1 Woche; danach täglich bis zum Start | `sendRSVPReminder` |
| `AppointmentReminderRule` | alle | 30 min | `sendReminder` |

`AppointmentReminderRule` wird nie erreicht, weil die drei Regeln davor jeden Termin abdecken, und `sendReminder` ist ohnehin leer ([BB-008](BB-008-events-und-ports.md)). Die Engine löst je Termin und Lauf höchstens eine Erinnerung aus (`break` nach dem ersten fälligen Zeitpunkt); ein Zeitpunkt gilt als fällig, wenn er nicht in der Zukunft liegt und höchstens 15 volle Minuten zurückliegt.

## Leader-Election

`LeaderElectionService` nutzt `LeaderElector` des Kubernetes-Clients mit `LeaseLock` `chronos-scheduler` im eigenen Namespace; Identität `HOSTNAME` (sonst Zufalls-UUID); Lease-Dauer 15 s, Verlängerungsfrist 10 s, Wiederholung 2 s, Freigabe beim Beenden. Mit `scheduler.leader-election.enabled=false` (`LEADER_ELECTION_ENABLED`, Standard `true`) ist jede Instanz Leader. Die RBAC-Rechte liefert `backend-role.yaml` ([VT-002](../07-verteilung/VT-002-kubernetes-ressourcen.md)). Ablauf: [LZ-006](../06-laufzeit/LZ-006-erinnerung-scheduler.md).
