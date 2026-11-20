---
id: AE-052
title: "Zeitgesteuerte Jobs mit Leader-Election"
status: entwurf
typ: architekturentscheidung
datum: 2026-02-24
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/LeaderElectionService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/scheduler/AppointmentReminderScheduler.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationValidationService.java
  - deployment/templates/backend-role.yaml
verknuepft: [AE-048, AE-054, G-010, QS-010]
---

# AE-052: Zeitgesteuerte Jobs mit Leader-Election

**Entscheidung:** Die Hintergrundaufgaben laufen als Quarkus-`@Scheduled`-Jobs (Cron `0 */15 * * * ?`) in jedem Backend-Pod; ausgeführt werden sie nur im Pod, der die Kubernetes-Lease `chronos-scheduler` hält (`LeaderElectionService`, fabric8 `LeaseLock`, Lease 15 s, Verlängerung 10 s, Retry 2 s; Identität = `HOSTNAME`). `scheduler.leader-election.enabled=false` (Tests, lokal) macht jeden Pod zum Leader; das Chart gibt dem Pod per Role Zugriff auf `leases`.
- Erinnerungen: `ReminderRuleEngine` wählt je Termin die erste passende `ReminderRule` (`ShortWeekdayRSVPRule`, `ShortWeekendRSVPRule`, `LongAppointmentRSVPRule` für Rückmeldeerinnerungen; `AppointmentReminderRule`, 30 min vor Beginn, gilt für alle Termine; Wirkung siehe [TD-008](../11-risiken/TD-008-30-minuten-erinnerung-ohne-wirkung.md)) und löst höchstens eine Erinnerung pro Lauf aus, wenn ein Triggerzeitpunkt höchstens 15 min zurückliegt; betrachtet werden Termine der nächsten 20 Wochen.
- Mindestteilnehmerzahl-Alarm: `sendNonMinimalAttendeesAlerts` prüft zu festen Fristen vor dem Termin (siehe [G-049](../12-glossar/G-049-mindestteilnehmerzahl.md)).
- Rückmelde-Bestätigung: `AppointmentParticipationStatusConfirmationService` löst 30 Tage vor langen und 7 Tage vor Wochenend-Terminen `AppointmentParticipationStatusConfirmationEvent` aus; nur `DebugLoggingService` beobachtet es ([TD-010](../11-risiken/TD-010-rueckmelde-bestaetigung-ohne-wirkung.md)).

**Begründung:** Mehrere Replicas (Autoscaling) dürfen Erinnerungen nicht doppelt versenden; die Lease braucht keine zusätzliche Infrastruktur im Kubernetes-Betrieb. Regeln als austauschbare Strategien halten die Fristlogik testbar.

**Alternativen:** Ein einziger Pod (verliert Hochverfügbarkeit); Quarkus-Scheduler im Cluster-Modus mit Datenbank-Lock; Kubernetes-CronJob. Eine dokumentierte Abwägung fehlt **(?)**. Alle drei Jobs prüfen `isLeader()` selbst; der Scheduler läuft in jedem Pod.

**Konsequenzen:** Das Backend hängt zur Laufzeit an der Kubernetes-API; bei Leaderwechsel können Läufe ausfallen oder wiederholt werden (Fenster 15 min = Intervall, kein Dedupe-Protokoll im Code; Einmaligkeit hängt allein an der Lease).
