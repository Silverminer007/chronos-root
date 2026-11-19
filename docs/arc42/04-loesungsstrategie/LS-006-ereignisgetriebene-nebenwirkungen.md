---
id: LS-006
title: Ereignisse für Nebenwirkungen
status: entwurf
typ: strategie
quellen:
  - kb/patterns/PATTERN-2025-09-28-002-cdi-events.md
  - backend/.../application/events
verknuepft: [KZ-003, BB-008]
---

# Ereignisse für Nebenwirkungen

Services lösen unveränderliche Event-Records aus (CDI `Event<...>`); `WebPushService`, Nachrichten- und Reminder-Logik beobachten sie asynchron. So bleibt die Fachlogik frei von Push-Details. Das Rust-Backend bildet das über einen Postgres-LISTEN/NOTIFY-Bus nach (KZ-003).
