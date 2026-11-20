---
id: AE-048
title: "Domänenereignisse über CDI-Events"
status: entwurf
typ: architekturentscheidung
datum: 2025-12-27
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/events/
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/WebPushService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/MessageService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java
verknuepft: [AE-010, AE-052, AE-053, KZ-003]
---

# AE-048: Domänenereignisse über CDI-Events

**Entscheidung:** Services führen die Kernänderung aus und lösen danach ein Ereignis aus (`jakarta.enterprise.event.Event`, Paket `application.events`, 26 Typen wie `AppointmentMovedEvent`, `AppointmentParticipationStatusChangedEvent`). Nebenwirkungen hängen als Observer daran, nicht als direkter Aufruf:
- Push (`WebPushService`) beobachtet fast alle Ereignisse mit `@Observes(during = AFTER_SUCCESS)` (14 Stellen), sendet also nur nach erfolgreichem Commit; Ausnahme siehe `ParticipationStatusChangedEvent`.
- Systemnachrichten (`MessageService`), Teilnehmer-Folgeaktionen (`AppointmentParticipationService`, zum Beispiel Ersteller wird `RESPONSIBLE`) und die Mindestteilnehmer-Prüfung (`AppointmentParticipationValidationService`) laufen als eigene Observer in `REQUIRES_NEW`-Transaktionen.
- `AppointmentParticipationStatusChangedEvent` (Zu-/Absage) geht per `fire` **und** `fireAsync` hinaus; alle vier Observer (`WebPushService`, `MessageService`, `AppointmentParticipationValidationService`, `DebugLoggingService`) sind `@ObservesAsync`, der synchrone Aufruf erreicht keinen Observer. Asynchrone Observer laufen sofort und unabhängig vom Commit des Auslösers; die Begründung ist nicht dokumentiert **(?)**.

**Begründung:** Push, Nachrichten und Validierung bleiben aus den Fachservices herausgehalten; neue Folgeaktionen brauchen nur einen weiteren Observer. `AFTER_SUCCESS` verhindert Pushes für zurückgerollte Änderungen.

**Alternativen:** Direkte Service-Aufrufe (enge Kopplung); externer Message-Broker (zusätzlicher Betriebsaufwand; im Projekt nicht erwogen **(?)**).

**Konsequenzen:**
- Der Ablauf ist implizit; ein Fehler in einem Observer ist vom auslösenden Request schwer zu trennen.
- Die Ereignisse leben nur im Prozess: Stürzt der Pod zwischen Commit und Observer ab, geht die Nebenwirkung verloren; Ereignisse werden nicht persistiert.
- Eigene Transaktionen der Observer machen deren Ergebnis unabhängig vom Haupt-Commit; bei der Zu-/Absage ist wegen `@ObservesAsync` auch der Zeitpunkt unabhängig vom Commit, ein Observer kann also vor dem Commit lesen oder bei Rollback trotzdem Push und Systemnachricht auslösen.
