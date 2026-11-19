---
id: AE-037
title: "One shared LISTEN connection for the Postgres event bus"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-024-event-bus-single-listener.md
verknuepft: []
---

# AE-037: One shared LISTEN connection for the Postgres event bus

Quelle: `kb/adr/ADR-2026-10-20-024-event-bus-single-listener.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

`PostgresEventBus::subscribe` opened a `PgListener` from the pool per event type. With 16 event types and the
default `DATABASE_MAX_CONNECTIONS` of 16 the listeners held every connection, so queries and the readiness probe
(`/q/health/ready`, `SELECT 1`) found no free connection. The need also grew with every new event type.

## Entscheidung

1. **One `PgListener` for all event types.** The first `subscribe` spawns a dispatcher task that owns a single
   listener; every further subscription is sent to it over an unbounded channel, which then issues `LISTEN` for
   the new channel on the same connection. The bus therefore always uses one pooled connection.
2. **Dispatch by channel.** The callbacks live in a `HashMap<event type, Vec<callback>>` inside the task. Several
   subscribers per event type are supported (as before, each had its own listener).
3. **Reconnect keeps all channels.** If `recv` or `LISTEN` fails, the task reconnects and re-listens on all
   registered channels (5 s delay when the connection cannot be opened). The task ends when the bus is dropped.
4. **Listener stays inside the pool** (instead of a separate connection) so no second connection setting is
   needed; the footprint is a constant 1. …

## Konsequenzen

(?) im ADR nicht ausgewiesen.
