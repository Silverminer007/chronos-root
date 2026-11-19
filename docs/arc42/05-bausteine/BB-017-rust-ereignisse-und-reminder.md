---
id: BB-017
title: Rust-Backend: Event-Bus und Reminder (Ebene 2)
status: entwurf
typ: baustein-ebene-2
quellen:
  - rust-backend/src/event_bus
  - rust-backend/src/reminders
  - kb/adr/ADR-2026-10-20-024-event-bus-single-listener.md
verknuepft: [KZ-003, LZ-006]
---

# Rust-Backend: Event-Bus und Reminder (Ebene 2)

`event_bus/postgres.rs`: ein gemeinsamer `PgListener` für alle Eventtypen, Wiederverbindung mit erneutem `LISTEN`. `reminders`: `rules.rs`, `scheduler.rs` (Tokio-Schleife alle 15 Minuten), `leader.rs` (Advisory-Lock auf getrennter Verbindung), `clock.rs` (`SystemClock`/`FakeClock`). Mindestens `DATABASE_MAX_CONNECTIONS >= 3`.
