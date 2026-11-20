---
id: LS-006
title: Ereignisse für Nebenwirkungen
status: entwurf
typ: strategie
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/events
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/WebPushService.java
verknuepft: [BB-008, KZ-003]
---

# Ereignisse für Nebenwirkungen

Services lösen unveränderliche Event-Records (CDI `Event<…>`) aus; Push, Systemnachrichten, Folgeteilnahmen und die Prüfung der Mindestteilnehmerzahl hängen als Beobachter daran. Die Fachlogik kennt damit keine Push-Details. Technik und Regeln: [KZ-003](../08-konzepte/KZ-003-events-und-event-bus.md).
