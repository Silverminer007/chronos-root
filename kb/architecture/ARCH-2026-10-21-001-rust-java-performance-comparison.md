---
title: ARCH-2026-10-21-001: Performance comparison of the Java and the Rust backend (local k3s)
date: 2026-10-21
component: Rust backend, Java backend, Deployment
relates_to:
  - ARCH-2026-10-20-001: Parity run of the Rust backend against the Java reference in the local k3s cluster
  - ARCH-2026-10-20-002: Final proof run of the Rust port
  - deployment/parity/perf/README.md: method and commands
---

## Summary

Both backends were measured one at a time in the namespace `chronos-parity` with the same limits (2 CPUs and 2 GiB each, Postgres 6 CPUs), the same
seed (16 users with own data) and two rounds in reverse order (`deployment/parity/perf/`). All four runs are valid: no restart, no request error.

| | Java | Rust | Reading |
|---|---:|---:|---|
| Start until ready | 15.8 s | 8.4 s | Rust 2x faster |
| Memory idle (anon) | 239 MiB | 1.4 MiB | Rust 170x less |
| Memory peak under load (anon) | 1,492 MiB | 22 MiB | Rust 70x less |
| Container image | 491 MiB | 21 MiB | Rust 23x smaller |
| Response time, one connection, reads | equal (median ratio 0.9-1.0) | | the network and the database dominate, not the language |
| Response time, one connection, writes | 11-18 ms | 24-39 ms | Rust 2-3.5x slower for appointment and message writes and RSVP; 1.2x for create group |
| Throughput under load, endpoints with database | 90-250 req/s | 25-196 req/s | Rust 0.3-0.8x |
| Throughput under load, `metrics` (no database) | 410 req/s | 809 req/s | Rust 2x |
| Mixed workload, 64 connections | 148 req/s | 57 req/s | Rust 0.4x |
| CPU, mixed workload | 2.0 cores (limit) | 1.8 cores | both use what they are given, see findings 3 and 4 |

**The Rust backend needs a fraction of the memory and starts faster, but it is not faster than the Java backend on the endpoints that use the database, and it is
slower on most writes (up to 3.5x).** The measured cause is not CPU work in the Rust code but the number of sequential database round trips per request, which is higher for
several Rust endpoints (finding 4), and the cost of every wake-up in this virtualised host (finding 3). The numbers hold for this host and these limits; see the limits section.

## Findings

1. **Both backends write on every authenticated request** (upsert of the caller into `user_profiles`; Java `LocalDbIdentityAdapter#upsert`, Rust `users::repository::upsert_identity`).
   With one user all requests wait for the same row: throughput falls from ~190-230 req/s with one connection to 36-52 req/s with 16, for Java and for Rust
   (`hot_row_single_user` in the results). A first measurement with one user showed ~35 req/s for every endpoint and both backends; it measured the row lock, not the backends.
   The load phases therefore spread the load over 16 users. This is also a design finding for production: a user who opens the app on two devices at the same time serialises on one row.
2. **Java is OOM-killed at the memory limit of the Helm chart.** With the chart value of 768 MiB the Java container exits with code 137 (`OOMKilled`) under 32 connections on
   `GET /api/v2/appointments/{id}?participants=true&messages=true&group_participants=true` (reproduced twice). The comparison therefore uses 2 GiB for both. Under load the JVM
   holds 1.0-1.5 GiB (`anon`) although the heap in use is only ~270 MiB (committed 272-286 MiB, non-heap 190 MiB); G1 spent 66.5-66.9 s in 3,500-4,000 pauses during the runs.
   The Rust peak of 22 MiB fits the chart limit of 128 MiB with a large margin. The CPU limit of the chart (Rust 0.5 CPU) was not measured.
3. **Most of the CPU time of the Rust process is kernel time for wake-ups in the virtual machine, not application code.** `perf` on the Rust process under load:
   96.7 % of the samples in the kernel (80 % in `_raw_spin_unlock_irqrestore`, then IPIs: `default_send_IPI_single_phys`, `smp_call_function_many_cond`, `kvm_kick_cpu`),
   3.3 % in `chronos_date_api`. The host is a KVM guest. Pinning all threads of the process to one CPU (`taskset`) lowered the CPU per request of `list appointments` from 42.7 ms to 2.6 ms and
   of `get user` from 7.8 ms to 0.6 ms at nearly the same throughput (58 instead of 45 req/s; `get user` 161 instead of 190 req/s; `pinning-rust.json`). The "CPU ms per request" columns
   therefore say more about this host (and about how often a thread is woken) than about the code; they must not be used as a prediction for other hardware. On bare metal, the Rust backend
   will very likely use much less CPU than shown here.
4. **Several Rust endpoints issue more sequential database round trips than the Java endpoints.** Transactions per request (`pg_stat_database.xact_commit`, 100 requests each; the counters are flushed with a delay, so
   read the values as approximate):

   | Endpoint | Java | Rust |
   |---|---:|---:|
   | get appointment | 1.2 | 4.9 |
   | create appointment | 2.9 | 5.7 |
   | send message | 2.9 | 10.8 |
   | list appointments | 6.0 | 5.6 |
   | list messages | 1.8 | 0.8 |
   | get user | 0.7 | 0.3 |

   Each statement outside an explicit transaction is a transaction with its own commit; in this host every round trip and every commit is expensive (finding 3, WAL flush). This is the best explanation
   for the 2-3.5x slower writes and for the lower throughput on `get appointment`, `patch appointment`, `send message` and the group endpoints. It is a lead for an optimisation ticket (fewer round trips: one
   transaction per request, joins instead of repeated lookups), not a proven root cause per endpoint.
5. **Rust rejects Keycloak tokens with an `aud` claim** (#174). Users that are created in the usual way (default roles) get `aud: account`; the Rust backend answers 401 (`InvalidAudience`), Java answers 200.
   The parity users have only the realm role `user` and no `aud`, so the contract run did not see it. The perf users are created like the parity users to be able to measure at all.
6. **The measurement floor.** A plain liveness probe reaches 15,000 (Java) and 30,600 (Rust) requests/s with 16 connections; with 64 connections Rust drops to 10,200 and Java reaches 20,000 (probably the two Tokio
   workers and the wake-ups of finding 3; not investigated). Below ~200 req/s per endpoint the load generator and the network are not the limit; the endpoints with a database are far below the floor.


## How to read the tables

- "Response time, no load" is measured with one connection and a Python client, 300 requests after 50 warm-up requests, for both backends alike. The endpoints that need Keycloak
  (`friend suggestions`, `admin list users`) measure Keycloak (dev mode, H2) as much as the backend.
- "Per endpoint under load" uses 32 connections spread over 16 users, 15 s per endpoint. The p99 is the worst p99 of the 16 load processes. Admin endpoints and the Keycloak endpoints are latency-only.
- "CPU ms/req" = CPU seconds of the backend container (cgroup) per completed request; includes kernel time (finding 3).
- "Mixed workload": 11 endpoints (25 % list appointments, 20 % get appointment, 10 % list messages, 10 % send message, 5 % each of get user, get settings, list groups, list friends,
  embedded appointment, create appointment, patch appointment); 45 s per step; the resource columns are averages and peaks of the one-second samples of that window. Postgres had 6 CPUs and used
  0.8-0.9 (Rust) and 1.8-2.4 (Java) cores.
- Raw data: `deployment/parity/perf/results/2026-10-21/` (per round and backend the summary and the one-second samples), diagnostics in `deployment/parity/perf/diagnostics/`.

## Limits of this measurement

- One host, a KVM guest with 14 vCPUs; the load generator ran on the same machine (pinned to four cores). Absolute numbers are not those of production hardware.
- Equal limits for both backends (2 CPUs, 2 GiB) are a choice. With the chart limits (Java 1 CPU / 768 MiB, Rust 0.5 CPU / 128 MiB) the Java backend is OOM-killed under this load (finding 2) and the
  Rust backend is throttled by its CPU limit (not measured).
- The Java image is the patched build of the parity run (`parity-patched`, participation routes, #162) and runs in JVM mode with the default JVM options; GraalVM native or tuned GC settings were not tried.
- The database is one Postgres instance for both backends (separate databases, one backend running at a time), 16 users, a few thousand rows. Larger data sets, other indexes or a remote database would change the balance
  (more round trips cost more with network latency, which would hurt the Rust endpoints with many round trips more).
- No endpoint with Web Push delivery, scheduler or leader election under load; no browser flows; the frontend was not run.
- Two rounds per backend; values whose rounds differ by more than 30 % carry a `~`.

## Reproduce

```bash
export OHA=/path/to/oha          # v1.16.0, see deployment/parity/perf/README.md
python3 deployment/parity/perf/bench.py --out results/round1 --order java,rust
python3 deployment/parity/perf/bench.py --out results/round2 --order rust,java
python3 deployment/parity/perf/report.py results/round1 results/round2 > tables.md
# diagnostics: perf profile, CPU pinning, transactions per request
OHA=$OHA python3 deployment/parity/perf/diagnostics/profile-rust.py "list appointments"
OHA=$OHA python3 deployment/parity/perf/diagnostics/pin-exp.py rust
python3 deployment/parity/perf/diagnostics/txn-count.py
```

## Changes made to the cluster for the measurement

`bench.py` sets equal limits and puts the original values back at the end. Kept: the Postgres deployment uses the `Recreate` strategy (a rolling update starts a second Postgres on the same data directory,
which crashes it); 16 Keycloak users `perf-user-1..16` (realm role `user`); `linux-perf` is installed on the host for the profile.

## Results

_2 round(s), averaged; `~` marks a value whose rounds differ by more than 30 %. Rust / Java ratio below 1 is better for Rust (latency, CPU, memory), above 1 is better for Rust (throughput)._

### Start, idle and peak memory

|  | Java | Rust | Rust / Java |
|---|---:|---:|---:|
| Start until `/q/health/ready` answers 200 (s) | 15.8 | 8.4 | 0.5x |
| Memory after start, idle, anon (MiB) | 239.2 | 1.4 | 0.0x |
| Memory after all load phases, idle, anon (MiB) | 669.8 | 5.1 | 0.0x |
| Memory peak under load, anon (MiB, without page cache) | 1,491.7 | 21.5 | 0.0x |
| Container image (containerd) | 491.3 MiB | 21.0 MiB |  |

### Measurement floor (plain liveness probe, no authentication, no database)

| Connections | Java req/s | Rust req/s | Java p50 (ms) | Rust p50 (ms) |
|---|---:|---:|---:|---:|
| 1 | 1,183 | 1,631 | 0.36 | 0.30 |
| 16 | 14,970 | 30,579 | 0.57 | 0.20 |
| 64 | 20,049 | 10,208 | 2.04 | 4.71 |

### Response time per endpoint, one connection, no load (ms)

| Endpoint | Status | Java p50 | Rust p50 | Rust / Java | Java p95 | Rust p95 |
|---|---:|---:|---:|---:|---:|---:|
| health ready | 200 | 1.55 | 1.33 | 0.9x | ~8.80 | 7.12 |
| metrics | 200 | 3.31 | 0.62 | 0.2x | ~17.41 | ~4.04 |
| get user | 200 | 5.17 | 2.99 | 0.6x | 21.89 | ~12.34 |
| get settings | 200 | 5.92 | 5.39 | 0.9x | 19.29 | 16.61 |
| list appointments | 200 | 13.56 | 13.99 | 1.0x | 42.78 | 41.94 |
| list appointments (embedded) | 200 | 14.76 | 14.54 | 1.0x | ~53.64 | 41.96 |
| get appointment | 200 | 6.51 | 7.71 | 1.2x | 24.34 | 30.27 |
| get appointment (embedded) | 200 | 8.34 | 11.75 | 1.4x | 26.31 | 40.89 |
| list participants | 200 | 7.26 | 6.76 | 0.9x | ~38.95 | ~24.98 |
| participation status | 200 | 4.61 | 4.25 | 0.9x | ~25.79 | ~16.46 |
| list messages | 200 | 8.40 | 6.77 | 0.8x | ~41.99 | 20.06 |
| list groups | 200 | 6.26 | 6.67 | 1.1x | 25.80 | 21.26 |
| list group users | 200 | 6.73 | 6.71 | 1.0x | ~23.45 | 21.82 |
| list friends | 200 | 6.20 | 5.17 | 0.8x | 23.03 | ~21.21 |
| incoming requests | 200 | 4.07 | 5.16 | 1.3x | ~18.15 | 16.91 |
| push public key | 200 | 3.56 | 2.72 | 0.8x | ~11.14 | 7.95 |
| admin stats | 200 | 33.71 | 27.14 | 0.8x | 118.58 | 79.84 |
| admin list appointments | 200 | 8.32 | 11.57 | 1.4x | 33.77 | 40.02 |
| friend suggestions (Keycloak) | 200 | 60.11 | 48.73 | 0.8x | 163.83 | 142.06 |
| admin list users (Keycloak) | 200 | 23.05 | 7.79 | 0.3x | 73.22 | ~28.80 |
| put settings | 200 | 5.82 | 5.29 | 0.9x | 18.01 | ~20.80 |
| create appointment | 201 | 11.05 | 38.67 | 3.5x | 44.52 | 107.66 |
| patch appointment | 200 | 12.46 | 23.89 | 1.9x | 41.06 | 79.85 |
| send message | 200 | 12.87 | 29.37 | 2.3x | 37.47 | 112.76 |
| create group | 200 | 12.62 | 14.92 | 1.2x | 45.36 | 52.47 |
| patch group | 200 | 7.09 | 16.39 | 2.3x | 24.54 | 59.23 |
| rsvp (alternating) | 200 | 17.89 | 38.02 | 2.1x | 88.59 | 116.63 |
| change role (alternating) | 200 | 7.85 | 17.36 | 2.2x | 24.99 | 49.23 |
| push subscribe | 204 | 8.62 | 7.43 | 0.9x | 23.19 | ~30.70 |
| push unsubscribe | 204 | 4.71 | 4.36 | 0.9x | ~15.29 | ~15.02 |

### Per endpoint under load: 32 connections, 15 s each

| Endpoint | Java req/s | Rust req/s | Rust / Java | Java p50 | Rust p50 | Java p99 | Rust p99 | Java CPU ms/req | Rust CPU ms/req | Errors J / R |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| health ready | 483 | 400 | 0.8x | 63.1 | 79.9 | 145.4 | 124.0 | 3.59 | 4.21 | 0 / 0 |
| metrics | 410 | 809 | 2.0x | 75.7 | 30.7 | 221.1 | 170.2 | 4.19 | 1.45 | 0 / 0 |
| get user | 248 | 194 | 0.8x | 125.0 | 162.2 | 264.2 | 261.9 | 6.62 | 6.06 | 0 / 0 |
| get settings | 222 | 101 | 0.5x | 136.9 | 312.4 | 321.9 | 454.4 | 7.76 | 15.09 | 0 / 0 |
| list appointments | 125 | 41 | 0.3x | 243.5 | 786.7 | 707.1 | 972.9 | 13.87 | 41.85 | 0 / 0 |
| list appointments (embedded) | 120 | 41 | 0.3x | 245.1 | 780.8 | 697.5 | 1,085.1 | 14.29 | 39.15 | 0 / 0 |
| get appointment | 201 | 71 | 0.4x | 154.1 | 446.9 | 341.7 | 642.4 | 8.98 | 24.48 | 0 / 0 |
| get appointment (embedded) | 149 | 44 | 0.3x | 204.7 | 730.4 | 479.9 | 905.5 | 12.02 | 36.71 | 0 / 0 |
| list participants | 195 | 82 | 0.4x | 161.1 | 385.5 | 353.5 | 519.7 | 8.86 | 19.01 | 0 / 0 |
| participation status | 233 | 137 | 0.6x | 132.4 | 232.3 | 275.3 | 329.4 | 7.32 | 11.51 | 0 / 0 |
| list messages | 160 | 76 | 0.5x | 195.1 | 420.8 | 458.7 | 589.5 | 10.82 | 21.34 | 0 / 0 |
| list groups | 178 | 77 | 0.4x | 176.1 | 415.6 | 404.6 | 604.9 | 9.73 | 20.80 | 0 / 0 |
| list group users | 180 | 81 | 0.4x | 178.6 | 392.7 | 432.1 | 551.1 | 9.90 | 19.93 | 0 / 0 |
| list friends | 188 | 98 | 0.5x | 169.4 | 322.3 | 389.4 | 528.5 | 9.19 | 15.28 | 0 / 0 |
| incoming requests | 227 | 104 | 0.5x | 137.9 | 304.3 | 269.3 | 469.1 | 7.54 | 15.19 | 0 / 0 |
| push public key | 238 | 196 | 0.8x | 130.4 | 161.3 | 266.1 | 263.6 | 7.07 | 6.04 | 0 / 0 |
| put settings | 151 | 104 | 0.7x | 211.8 | 303.5 | 425.4 | 444.9 | 11.54 | 11.40 | 0 / 0 |
| create appointment | 104 | 50 | 0.5x | 302.6 | 637.4 | 547.1 | 835.6 | 16.46 | 26.78 | 0 / 0 |
| patch appointment | 88 | 25 | 0.3x | 352.7 | 1,299.2 | ~892.1 | 1,591.3 | 20.23 | 65.89 | 0 / 0 |
| send message | 89 | 50 | 0.6x | 351.2 | 644.1 | 735.3 | 807.3 | 20.05 | 28.35 | 0 / 0 |
| create group | 105 | 46 | 0.4x | 298.3 | 686.5 | 577.6 | 841.0 | 16.96 | 29.39 | 0 / 0 |
| patch group | 135 | 40 | 0.3x | 229.2 | 792.5 | 508.8 | 985.5 | 12.77 | 38.46 | 0 / 0 |

### Mixed workload, ramp of concurrent connections (60 % reads of 11 endpoints, 20 % writes), 45 s per step

| Connections | Backend | Req/s | p95 worst endpoint (ms) | Errors | CPU cores avg | CPU cores peak | Memory peak anon (MiB) | Postgres cores avg | Backend CPU ms/req |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 16 | Java | 62 | 2,183 | 0 | 1.69 | 1.85 | 1,065 | 1.76 | 25.69 |
| 16 | Rust | 41 | 1,293 | 0 | 1.84 | 1.92 | 6 | 0.81 | 41.48 |
| 64 | Java | 148 | ~2,486 | 0 | 2.00 | 2.13 | 1,301 | 2.39 | 12.91 |
| 64 | Rust | 57 | 3,143 | 0 | 1.84 | 1.92 | 8 | 0.88 | 30.73 |
| 256 | Java | 144 | 4,869 | 0 | 1.99 | 2.09 | 1,492 | 2.01 | 13.10 |
| 256 | Rust | 63 | 12,341 | 0 | 1.77 | 1.91 | 22 | 0.89 | 27.22 |
