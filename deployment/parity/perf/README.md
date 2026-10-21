# Performance comparison: Java and Rust backend

Local k3s only, namespace `chronos-parity` (see `../README.md`). Measures response times per endpoint and CPU and memory under load
for the Java backend (reference) and the Rust backend. Results and interpretation: `kb/architecture/ARCH-2026-10-21-001-rust-java-performance-comparison.md`.

```bash
# prerequisites: kubectl for the cluster, Python 3.11, taskset, `oha` (load generator)
curl -sL -o oha https://github.com/hatoo/oha/releases/download/v1.16.0/oha-linux-amd64
echo "620bb9e16fb53eabc9a3fc45f88bdb41fefa3fee5c05e75892011ce320391716  oha" | sha256sum -c -   # then: chmod +x oha
export OHA=$PWD/oha

# two rounds, the second one in reverse order (cancels out order effects); about 30 minutes per round
python3 deployment/parity/perf/bench.py --out results/round1 --order java,rust
python3 deployment/parity/perf/bench.py --out results/round2 --order rust,java
python3 deployment/parity/perf/report.py results/round1 results/round2 > tables.md
```

`--quick` runs a short smoke test (4 users, a few seconds per phase).

## Method

- **One backend at a time.** The other deployment is scaled to 0. The database of the backend is dropped and created again, the backend is started
  and measured from the start (time until `/q/health/ready` answers 200, includes the schema migration on an empty database).
- **Same limits.** Both backends get the same limits (2 CPUs, 2 GiB; the chart values differ: Java 1 CPU / 768 MiB, Rust 0.5 CPU / 128 MiB).
  Postgres gets 6 CPUs and 2 GiB, because 2 CPUs were saturated by the mixed workload and the database would have been the cap
  instead of the backends. `bench.py` sets the limits at the start and puts the original values back at the end. The originals are kept as the annotation
  `perf-original-resources` on the deployments, so a run that was killed does not make the next run take the benchmark values for the originals;
  `results/<date>/original-limits.json` shows the values of the chart before the first run.
  Postgres is switched to the `Recreate` strategy (a rolling update starts a second Postgres on the same data directory, which
  crashes it).
- **No proxy on the data path.** Requests go to the pod IP, not through `kubectl port-forward`. Tokens come from Keycloak once.
- **Load generator.** `oha`, one process per user and one worker thread per process, pinned to cores 10-13 (`OHA_CPUS`).
- **Same seed.** `PERF_USERS` (default 16) users `perf-user-N` (realm role `user` only, like the parity users) with own data each:
  3 appointments, 20 messages, a group, a friend who is also a participant.
- **CPU and memory** are read once per second from the cgroup of the backend container and of Postgres
  (`cpu.stat usage_usec`, `memory.current`, `memory.stat anon`). The report uses `anon` (working set without page cache) for memory.
  For the JVM, heap and GC come from the Micrometer metrics.

## Phases

1. **Idle**: memory and CPU 15 s after the backend is ready.
2. **Warm-up** (not measured): the mixed workload for 30 s, so that the JIT has compiled the hot paths.
3. **Measurement floor**: plain liveness probe (no authentication, no database) with 1, 16 and 64 connections. It is the ceiling of pod
   network, VM and load generator; endpoints near it cannot show a difference between backends.
4. **Response time per endpoint, no load**: one keep-alive connection, 300 requests after 50 warm-up requests, same Python client for both
   backends. Includes endpoints that change state back and forth (RSVP, role) and the Keycloak-bound ones.
5. **Per endpoint under load**: 32 connections for 15 s, spread over the users (16 processes with 2 connections). Admin endpoints and the
   Keycloak-bound endpoints are latency-only (one admin user would be a hot row again).
6. **Mixed workload**: 60 % reads of 11 endpoints, 20 % writes, 256, 64 and 16 connections in total, 45 s per step, with the CPU
   and memory samples of that window.
7. **Hot row**: one user, 1, 4 and 16 connections.

## What to know before reading the numbers

- **Both backends write on every request.** The identity of the caller is upserted into `user_profiles` (Java `LocalDbIdentityAdapter#upsert`,
  Rust `users::repository::upsert_identity`). With one user all requests wait for the same row, so throughput falls from ~190 req/s with one
  connection to ~35 req/s with 16, for both backends (`hot_row_single_user`). The load phases therefore use many users.
- **Tokens with an `aud` claim are rejected by the Rust backend** (#174). The perf users get only the realm role `user`, like the parity
  users, otherwise they get `aud: account` and a 401.
- **At the memory limit of the chart (768 MiB) the Java backend is OOM-killed** under 32 connections on
  `GET /appointments/{id}?participants=true&messages=true&group_participants=true` (seen twice, exit code 137). The comparison therefore runs with 2 GiB.
- `p99` of an endpoint under load is the worst p99 of the processes (a merged percentile needs the raw samples); p50 and p95 are weighted by
  the number of requests.
- The percentiles of the unloaded phase come from a Python client: its own overhead is the same for both backends, but it is part of
  every number (see the measurement floor).
- The VM is probably virtualised: absolute numbers (CPU per request, the ceiling of ~20k req/s on a trivial endpoint) are not those of production
  hardware. Compare the backends with each other, not with other systems.

## Files

| File | Content |
|---|---|
| `bench.py` | the benchmark (one round) |
| `report.py` | markdown tables from the results of one or more rounds |
| `results/<date>/roundN/{java,rust}.json` | results; `*-samples.json` are the once-per-second samples of cpu and memory |
| `results/<date>/txn-per-request.json`, `pinning-rust.json` | database transactions per request; CPU pinning experiment |
| `diagnostics/profile-rust.py` | `perf` profile of the Rust process under load (needs `linux-perf`, `sudo`) |
| `diagnostics/pin-exp.py` | same load unpinned, on one and on two CPUs (`taskset`) |
| `diagnostics/txn-count.py` | database transactions and rows per request from `pg_stat_database` |

The diagnostics need `OHA` and write to `/tmp/perf-diag`; they set the same limits as `bench.py` and put the original ones back.
