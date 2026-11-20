---
id: KZ-003
title: Ereignisse und CDI-Events
status: entwurf
typ: konzept
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/events
verknuepft: [LS-006, BB-008]
---

# Ereignisse und CDI-Events

Services lösen `jakarta.enterprise.event.Event<…>`-Ereignisse aus; die Beobachter und ihre Zuordnung stehen in [BB-008](../05-bausteine/BB-008-events-und-ports.md). Regeln:

- Event-Records sind unveränderlich und tragen nur IDs und Auslöser (`appointmentId`, `actingUserOidcId`, …); Beobachter laden die Daten selbst neu.
- Zwei Beobachtungsarten: synchron mit `@Observes(during = AFTER_SUCCESS)`, also erst nach dem Commit der auslösenden Transaktion (Push, Folgeteilnahmen), oder `@Observes` direkt (Systemnachrichten bei Verschiebung/Absage, Prüfung bei Terminänderung). Nur `AppointmentParticipationStatusChangedEvent` wird zusätzlich asynchron (`fireAsync`/`@ObservesAsync`) beobachtet, damit Zu-/Absagen nicht auf Push und Prüfung warten.
- Jeder Beobachter, der schreibt, nutzt eine eigene Transaktion (`REQUIRES_NEW`), sie läuft damit unabhängig von der auslösenden Transaktion.
- Beobachter können mehrere Ereignisse auslösen (z. B. Statusprüfung ⇒ `…Invalid`); Zyklen vermeidet die Fachlogik, nicht der Mechanismus.
- Ereignisse existieren nur in einer Instanz; verteilt wird nichts ([LS-012](../04-loesungsstrategie/LS-012-leader-election-fuer-zeitgesteuerte-aufgaben.md) für Zeitaufgaben).
