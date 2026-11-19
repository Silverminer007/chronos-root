---
id: AE-030
title: "Contract parity harness for the Rust port"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-021-contract-parity-harness.md
verknuepft: []
---

# AE-030: Contract parity harness for the Rust port

Quelle: `kb/adr/ADR-2026-10-20-021-contract-parity-harness.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Ticket #136. The Rust backend (`rust-backend/`) must be a drop-in replacement for the Java backend (`backend/`, the reference). Per-endpoint tests in each ticket check single endpoints; nothing checks the whole surface against the reference, or notices an endpoint that Rust still lacks. The harness was started before all endpoint tickets were merged, so it must not depend on the state of the Rust code.

## Entscheidung

- **Location and language**: `contract-tests/`, Python 3 with the standard library only (no dependencies, no build step, runs on every CI runner). It lives outside `backend/` and `rust-backend/src/` so it does not collide with endpoint tickets. Tests are `unittest`.
- **One command**: `python3 -m parity` from `contract-tests/` (see its README). It **targets** two running backends by URL (`--java-url`, `--rust-url`). Starting the backends and seeding their databases (Flyway for Java, `rust-backend/migrations` baseline for Rust) is left to the caller, locally with Docker or in the cluster (#137), because both need Keycloak and a database whose setup differs per environment. The harness itself creates every row it needs through the API, so no SQL seed is shared between the two databases …

## Konsequenzen

- Both backends mutate their database during a run (the cleanup steps remove what the scenario creates, but e.g. settings and the admin befriend step persist). Run against fresh databases for reproducible results.
- The Java source parsing is regex-based. A resource that builds paths dynamically would be missed; `--coverage` would then report the count mismatch only if the endpoint is not found at all, so new resource styles need a look at `parity/endpoints.py`.
- Pairs of steps in the scenario run against shared users, so the scenario relies on a second seeded user (`CONTRACT_OTHER_USER_ID`, default `oidc-user-2`).
