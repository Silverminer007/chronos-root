---
title: ARCH-2026-10-21-002: What limits the throughput of the Rust backend, and how to remove it
date: 2026-10-21
component: Rust backend
relates_to:
  - ARCH-2026-10-21-001: Performance comparison of the Java and the Rust backend (local k3s)
  - deployment/parity/perf/README.md: benchmark, diagnostics and prototypes
---

## Summary

The throughput of the Rust backend is not limited by CPU, by the SQL work or by the size of the connection pool. It is limited by **the number of sequential database round trips per
request, and by the cost of each of them in this host**. Two of the round trips are pure overhead (a write on every request and a ping before every query). Removing the
three cheapest causes in a prototype (no change of the API contract) raised the throughput at 32 connections from **172 to 2,687 req/s on `get user` (15.6x), from 61 to 361 on
`get appointment` (6.0x)**, and by 1.9x to 2.4x on list, create and send. The plan below orders the changes by measured gain per effort.

All numbers: 16 users with own data, 32 connections, 15 s per endpoint, the Rust backend on 2 CPUs on the host, Postgres in the cluster (6 CPUs), measurement noise about +-20 % on read endpoints
and +-3 % on writes (v2 measured twice). Method: `deployment/parity/perf/` (benchmark), `diagnostics/` (profile, pinning, transactions, wait events), `prototypes/` (the patches).

## Figure 1: where one request spends its round trips (`GET /api/v2/appointments/{id}`)

```
BASELINE                                                                       round trips
GET /api/v2/appointments/{id}
|
+- JWT check (in memory)                                                              0
+- cache_identity  INSERT INTO user_profiles ... ON CONFLICT DO UPDATE                2   <== (1) a write + COMMIT
|                    ping (1) + statement with COMMIT and WAL flush (1)                       on every request, one
|                                                                                             hot row per user
+- role lookup     SELECT role FROM appointment_participation ...                     2   <== (2) sqlx pings the
+- appointment     SELECT ... FROM appointment WHERE id = ...                         2       connection before every
+- group members   SELECT ... FROM group_member WHERE group_id = ANY('{}')            2   <== (3) nothing to fetch
+- names           SELECT ... FROM user_profiles WHERE oidc_id = ANY('{}')            2   <== (3) nothing to fetch
                                                                                     --
                                                                                     10   p50 513 ms at 32 connections

AFTER (prototype, changes A + B + D)
GET /api/v2/appointments/{id}
|
+- JWT check (in memory)                                                              0
+- cache_identity  claims unchanged for 5 min: no query                               0   (1) gone
+- role lookup     SELECT role ...                                                    1   (2) gone
+- appointment     SELECT ... FROM appointment WHERE id = ...                         1
                                                                                     --
                                                                                      2   p50 88 ms at 32 connections, 6.0x
```

## Figure 2: where the requests wait (evidence)

```
                  32 requests in flight
  oha  ------->  axum / tokio  ------------>  sqlx pool  ------------>  Postgres
                 2 worker threads             16 connections            WAL, locks, tables
                 |                            |                         |
                 | 96.7 % of the CPU time     | 8 to 10 connections     | 83 - 98 % of the connections
                 | of the process is KERNEL   | open, never all in use  | are `idle / ClientRead`:
                 | (thread wake-ups, IPIs     |                         | the database WAITS FOR THE APP
                 | in a KVM guest), 3.3 % is  |                         | (46 % on `get user`, where the
                 | our code (perf)            |                         | commit queue `WALWrite` is 39 %)
                 |                            |                         |
  more CPUs (2 -> 8):        get appointment 73 -> 108 req/s (+47 %), get user unchanged (190 req/s)
  pinned to ONE cpu:         CPU per request 42.7 -> 2.6 ms (list appointments), throughput only +30 %
  synchronous_commit = off:  get user +48 %, get appointment +38 %, send message +41 %, list +- 0
  -------------------------------------------------------------------------------------------------
  => the limit is  (round trips per request) x (cost of one wake-up/round trip),
     not CPU quota, not pool size, not the SQL execution.
```

## Figure 3: throughput of the prototype against the baseline (req/s at 32 connections, one bar scale per endpoint)

```
get user           baseline  ██                                 172
                   prototype ██████████████████████████████   2,687   15.6x   p50 179 -> 10 ms

get appointment    baseline  █████                               61
                   prototype ██████████████████████████████     361    6.0x   p50 513 -> 88 ms

list appointments  baseline  ████████████                        45
                   prototype ██████████████████████████████     109    2.4x   p50 717 -> 298 ms

send message       baseline  ████████████████                    43
                   prototype ██████████████████████████████      82    1.9x   p50 737 -> 404 ms

create appointment baseline  █████████████                       44
                   prototype ██████████████████████████████     103    2.4x   p50 730 -> 310 ms
```

## Figure 4: the bottlenecks in order of measured effect

```
rank  bottleneck                                      measured effect (prototype)            effort  risk
----  ----------------------------------------------  -------------------------------------  ------  ------
 1    write of the caller's profile on every request  get user 172 -> 3,119 (18x)             S       low
      (INSERT ... ON CONFLICT + COMMIT, hot row)      get appointment 61 -> 125 (2.1x)
                                                      list +39 %, send +26 %, create +27 %
 2    ping before every pool acquire                  get appointment 125 -> 254 (2.0x)      XS      low
      (sqlx test_before_acquire, 1 extra round        list appointments 63 -> 114 (1.8x)
      trip per statement)                             send +27 %, create +20 %
 3    queries with an empty id list in with_details   get appointment 190 -> 361 (1.9x)      XS      none
      (ANY('{}') returns nothing)                     list +21 %, send +32 %
 4    events through INSERT + NOTIFY + SELECT         create appointment 67 -> 106 (1.6x)    M       medium
      (2 commits and 1 query per event)               send message: no gain (handler cost)
 5    list query reads every appointment              list 81 -> 105 req/s at 51,000 rows    S       low
      (no index on appointment_participation          (+30 %); with 1,300 rows the effect is
       .user_oidcid, COUNT + page = 2 scans)          small
 6    several commits per write                       not measured (create: 5 commits,       M       medium
      (create appointment: 5, send message: 4)        Java: 3 / 2)
 7    wake-up cost of the VM (cross-CPU IPIs)         CPU per request -94 % when pinned      ?       env
 --   insert + notify in ONE statement                send message 68 -> 26 (-60 %)          -       DO NOT
```

## Findings

1. **The profile is written on every request.** `cache_identity` runs `INSERT INTO user_profiles ... ON CONFLICT DO UPDATE` before every handler, with its own commit (a copy of
   Java `LocalDbIdentityAdapter#upsert`). It caps the cheapest endpoint (`get user`) at 170-250 req/s in both backends, although the Rust code serves 3,000 req/s without it. It is also a hot row:
   all requests of one user wait for each other (36-52 req/s with 16 connections of one user).
2. **Every query costs two round trips.** The pool is created without `test_before_acquire(false)`, so sqlx checks each connection before handing it out. With the check removed, `get appointment`
   doubled and `list appointments` grew by 82 %; the CPU per request halved.
3. **Queries without work.** `with_details` always runs the group-member and the name query, also with an empty id list (every `get appointment` without `?participants=...`, every list).
4. **Events go through the database.** `fire` inserts into `events`, calls `pg_notify`, and the listener loads the event again with a `SELECT` before it calls the handlers: two commits and a query per event,
   although the handlers run in the same process (Java: CDI, in the JVM). Postgres takes a global lock for the commit of a transaction that sends a notification, which serialises the commits of events.
   Putting the insert and the notification in one statement made it worse: send message 68 -> 26 req/s (reproduced, the writes vary by +-3 % otherwise).
5. **The list query scans.** `search_visible` filters appointments by `EXISTS (... ap.user_oidcid = $1)` over all appointments in the window of 1,000 years, twice (`COUNT(*)` and the page). `EXPLAIN` with
   only 1,250 appointments: Seq Scan on `appointment` and on `appointment_participation` (1,188 rows removed), 6 ms for the count and 4.6 ms planning. With 51,000 appointments the throughput fell
   from 109 to 81 req/s; an index on `(user_oidcid, appointment_id)` brings it back to 105.
6. **The host.** 96.7 % of the CPU time of the process is kernel time (`_raw_spin_unlock_irqrestore`, IPIs, `kvm_kick_cpu`); the process is a guest of KVM, every wake-up of an idle vCPU is expensive.
   Pinned to one CPU the process needs 2.6 instead of 42.7 ms per request. This is why fewer round trips help so much here (each one is a wake-up); the absolute numbers will differ on other hardware, the order
   of the causes very likely does not, because fewer queries and no write per request are cheaper everywhere.
7. **Not the cause:** the CPU limit (2 -> 8 CPUs: +47 % on one endpoint, 0 % on the other, 1.2 - 2.4 cores used), the pool size (8 - 10 connections open of 16), Postgres CPU (0.8 - 0.9 cores), JWT validation, the JSON code and all other application code (3.3 % of the CPU time
   together), the allocator (below 0.5 % in malloc/free).
8. **Java has the same two main causes** (write on every request; the same list query, schema without the index). It does not pay for the ping and issues fewer separate transactions, which is why it is ahead
   on the endpoints of this table. The first two plan items would help Java as well.

## Plan

The order is by measured gain per effort. Each step is a ticket of its own; after each one the contract harness (`contract-tests`, parity run) must stay green, `cargo test` passes, and
the benchmark of `deployment/parity/perf/diagnostics/host-run.py` shows at least the gain stated below (same host, same data, compared with the commit before).

**Phase 1: three small changes, no behaviour change (expected: get user 15x, get appointment 4-6x, others 1.5-2.4x)**

| # | Ticket | Change | Gain to verify | Notes and risks |
|---|---|---|---|---|
| 1 | Identity upsert only when needed | In `cache_identity`, skip the upsert while the claims (name, e-mail, picture) of the user are unchanged and were stored less than N minutes ago. Bounded cache (for example `moka`, 10,000 entries, TTL 5 min) in the `UserRepository`. Patch: `prototypes/A-identity-upsert-only-when-changed.patch` | get user >= 10x, get appointment >= 1.8x | `updated_at` is refreshed at most every N minutes; changed claims are stored at once (fingerprint). On a user deletion the entry has to be removed, or the TTL stays short (60 s). Several replicas: each has its own cache, which is correct. |
| 2 | Ping only for idle connections | `before_acquire` that pings only when `idle_for` is above 30 s, instead of the ping on every acquire (`PgPoolOptions::before_acquire`). The prototype used `test_before_acquire(false)`: `prototypes/B-no-ping-before-acquire.patch` | get appointment >= 1.7x, list >= 1.5x | After a restart of Postgres the first query on a stale connection may fail; `max_lifetime`, `idle_timeout` and the 30 s rule keep this rare. A retry of reads on connection errors closes the gap. |
| 3 | No query for empty id lists | Skip `list_group_members` and `find_identities` when the id list is empty (`with_details`, other `ANY($1)` calls). Patch: `prototypes/D-skip-empty-id-queries.patch` | get appointment +80 % (190 -> 360) | None. |

**Phase 2: events and round trips (expected: writes 1.5-2x, create appointment 1.6x)**

| # | Ticket | Change | Gain to verify | Notes and risks |
|---|---|---|---|---|
| 4 | Events in the process | `fire` calls the registered callbacks directly (same as Java CDI); no `INSERT`, `NOTIFY`, `SELECT` on the hot path. The `events` table stays only if an audit trail is wanted (then written asynchronously, batched). Patch: `prototypes/E-events-in-process.patch` | create appointment >= 1.5x | Decision for the owner: durability and delivery to other replicas (today every replica receives every `NOTIFY`, so with 2 replicas each push notification is sent twice; in-process delivery avoids that). The reminder scheduler and the tests of the bus have to be adapted. |
| 5 | Handler work of the events | `MessageSentEvent` and `AppointmentCreated...` handlers: one query instead of four (`message` + `appointment` + names + participants), off the request path with a bounded queue. | send message >= 1.5x | The handlers decide the CPU of `send message` (22-26 ms per request even after step 4). |
| 6 | One transaction per write | create appointment (5 commits), send message (4), RSVP, patch: one transaction, the participant row of the creator in the same one. | create/patch/send >= 1.3x | Behaviour on failure becomes atomic (today a failed second insert leaves a half-created appointment). Java uses 3 / 2 commits. |
| 7 | Fewer round trips on reads | Authorisation and loading in one statement (join), `tokio::try_join!` for independent queries in `with_details`. | get appointment, list +20 % | More SQL per statement; keep one test per endpoint for the response shape. |

**Phase 3: data volume**

| # | Ticket | Change | Gain to verify | Notes and risks |
|---|---|---|---|---|
| 8 | Index and one query for the list | Migration `CREATE INDEX idx_appointment_participation_user ON appointment_participation (user_oidcid, appointment_id)`; the list query from the participation side; `COUNT(*) OVER()` or `LIMIT/OFFSET` plus one count only for page 0. | list at 51,000 rows: 81 -> 105 req/s (measured) and flat with growing data | The Rust schema follows the Java schema (ticket #95): an additive index is no contract change; Java would profit from the same index. |

**Phase 4: only after the numbers of a real node**

| # | Ticket | Change | Notes |
|---|---|---|---|
| 9 | Runtime on the target hardware | Repeat `deployment/parity/perf` on the node type of production; test `worker_threads`, one `current_thread` runtime per CPU (SO_REUSEPORT) and `mimalloc`. | In this KVM guest wake-ups cost 94 % of the CPU time; on other hardware the gain may be small. Do not tune before measuring there. |
| 10 | Log volume | `Event fired` and `Received notification` at `debug` (two INFO lines per event, 1,642 events in one 15 s run). | Small; the logs of the container go through containerd. |

**Do not do**

- Merge the event insert and `pg_notify` into one statement: writes get 60 % slower (measured twice).
- `synchronous_commit = off` for the whole database: +38-48 % on the write paths in the experiment, but a crash may lose the last transactions. For the profile cache alone, step 1 gives much more.
- Raise the CPU limit or the pool size to get more throughput: no effect on the plateau (finding 7).

## Expected result of phase 1 and 2 (prototype A + B + E + D, measured)

| Endpoint | Baseline req/s | Prototype req/s | Factor | p50 (ms) |
|---|---:|---:|---:|---|
| get user | 172 | 2,687 | 15.6x | 179 -> 10 |
| get appointment | 61 | 361 | 6.0x | 513 -> 88 |
| list appointments | 45 | 109 | 2.4x | 717 -> 298 |
| send message | 43 | 82 | 1.9x | 737 -> 404 |
| create appointment | 44 | 103 | 2.4x | 730 -> 310 |

Step by step (req/s, rows are the cumulative prototypes; v2 was measured twice, the second value in brackets):

| Prototype | get user | get appointment | list appointments | send message | create appointment |
|---|---:|---:|---:|---:|---:|
| baseline | 172 | 61 | 45 | 43 | 44 |
| + A identity upsert | 3,119 | 125 | 63 | 54 | 55 |
| + B no ping | 3,057 (3,173) | 254 (200) | 114 (95) | 68 (70) | 67 (67) |
| + E events in process | 3,198 | 190 | 90 | 62 | 106 |
| + D no empty queries | 2,687 | 361 | 109 | 82 | 103 |
| (insert + notify in one statement, C) | 3,008 | 189 | 99 | 26 | 27 |

## Evidence and how it was measured

| Question | Method | Result |
|---|---|---|
| What does one request send to the database? | `log_statement = all` for one request per endpoint, Java and Rust (`diagnostics/anatomy.py`) | `get appointment` Rust: 5 statements (1 commit), Java 3; `send message` Rust: 4 commits (upsert, message, event, notify), Java 2; create appointment 5 against 3 |
| Where do the database connections wait? | `pg_stat_activity` sampled every 100 ms during the load, with `synchronous_commit` on and off (`diagnostics/waits.py`) | 83-98 % `idle / ClientRead` on reads, 46 % on `get user`; `WALWrite` 39 % on the paths with a commit |
| Is it CPU? | `perf record -F 499 -g` of the Rust process under load; CPU pinning (`diagnostics/pin-exp.py`); CPU limit 2 against 8 (`diagnostics/scaling.py`) | 96.7 % kernel time; 42.7 -> 2.6 ms per request when pinned; plateau from 16 connections whatever the limit |
| What does each change bring? | five prototypes built from `origin/main` `37c717c`, run on the host with 2 CPUs against the same Postgres (`diagnostics/host-run.py`), data and load as in `bench.py` | tables above; raw data in `results/2026-10-21/prototypes/` |
| Does the data volume matter? | 50,000 appointments added, with and without the index | list 109 -> 81 -> 105 req/s |
| Is the measurement stable? | v2 measured twice | reads +-20 %, writes +-3 % |

Limits: one virtualised host (KVM guest, 14 vCPUs); the Rust backend ran on the host (with `taskset` on two CPUs) in the prototype runs, not in a pod; the baseline of this set-up (172 / 61 / 45 / 43 / 44 req/s) is
close to the in-pod run (194 / 71 / 41 / 50 / 50). The prototypes are not tuned code: they show what the causes cost, not the final implementation. The patches are in `deployment/parity/perf/prototypes/`
and apply to `origin/main` `37c717c`.
