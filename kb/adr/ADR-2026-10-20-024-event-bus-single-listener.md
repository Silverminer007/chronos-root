# ADR-2026-10-20-024: One shared LISTEN connection for the Postgres event bus

- Status: accepted
- Ticket: #153 (found in the parity run of #137)

## Context

`PostgresEventBus::subscribe` opened a `PgListener` from the pool per event type. With 16 event types and the
default `DATABASE_MAX_CONNECTIONS` of 16 the listeners held every connection, so queries and the readiness probe
(`/q/health/ready`, `SELECT 1`) found no free connection. The need also grew with every new event type.

## Decisions

1. **One `PgListener` for all event types.** The first `subscribe` spawns a dispatcher task that owns a single
   listener; every further subscription is sent to it over an unbounded channel, which then issues `LISTEN` for
   the new channel on the same connection. The bus therefore always uses one pooled connection.
2. **Dispatch by channel.** The callbacks live in a `HashMap<event type, Vec<callback>>` inside the task. Several
   subscribers per event type are supported (as before, each had its own listener).
3. **Reconnect keeps all channels.** If `recv` or `LISTEN` fails, the task reconnects and re-listens on all
   registered channels (5 s delay when the connection cannot be opened). The task ends when the bus is dropped.
4. **Listener stays inside the pool** (instead of a separate connection) so no second connection setting is
   needed; the footprint is a constant 1. Pools need `DATABASE_MAX_CONNECTIONS >= 2`.
5. **Removed `create_listener`**, an unused public helper that encouraged one connection per channel.
6. **Test:** `test_subscriptions_leave_pool_connections_free` subscribes 16 event types on a pool of 16 and
   requires a query and an event delivery to still work (red before the change: `PoolTimedOut`).
