---
id: KZ-003
title: Ereignisse und Event-Bus
status: entwurf
typ: konzept
quellen:
  - kb/patterns/PATTERN-2025-09-28-002-cdi-events.md
  - kb/adr/ADR-2026-10-20-024-event-bus-single-listener.md
verknuepft: [LS-006, BB-008, BB-017]
---

# Ereignisse und Event-Bus

Java: Services feuern CDI-Events (`@Inject Event<…>`), Observer verarbeiten sie asynchron. Rust: `EventBus`-Trait mit Postgres-Implementierung (LISTEN/NOTIFY), ein Listener für alle Typen (ADR-024), je Typ mehrere Abonnenten; Fachlisteners in `*/event_listeners.rs`. Ereignis-Records sind unveränderlich und enthalten nur IDs.
