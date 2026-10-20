---
title: ADR-2026-10-20-021: Contract parity harness for the Rust port
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

Ticket #136. The Rust backend (`rust-backend/`) must be a drop-in replacement for the Java backend (`backend/`, the reference). Per-endpoint tests in each ticket check single endpoints; nothing checks the whole surface against the reference, or notices an endpoint that Rust still lacks. The harness was started before all endpoint tickets were merged, so it must not depend on the state of the Rust code.

## Decision

- **Location and language**: `contract-tests/`, Python 3 with the standard library only (no dependencies, no build step, runs on every CI runner). It lives outside `backend/` and `rust-backend/src/` so it does not collide with endpoint tickets. Tests are `unittest`.
- **One command**: `python3 -m parity` from `contract-tests/` (see its README). It **targets** two running backends by URL (`--java-url`, `--rust-url`). Starting the backends and seeding their databases (Flyway for Java, `rust-backend/migrations` baseline for Rust) is left to the caller, locally with Docker or in the cluster (#137), because both need Keycloak and a database whose setup differs per environment. The harness itself creates every row it needs through the API, so no SQL seed is shared between the two databases.
- **Same user on both sides**: one bearer token (user, plus admin for `/api/v2/admin/*`) is sent to both backends. Both validate against the same Keycloak, so the same token is the same test user. Tokens come from `CONTRACT_USER_TOKEN`/`CONTRACT_ADMIN_TOKEN` or are fetched with a password grant (`CONTRACT_TOKEN_URL`, ...). No credentials are stored in the repository.
- **Scenario, not a request list**: `parity/scenario.py` is an ordered list of steps. Steps capture ids (`group`, `appt`, ...) from responses **per backend**, because generated ids differ. A step whose capture failed continues with id `0`, so the missing resource appears as a status mismatch and not as a silent skip.
- **What is compared** (`parity/compare.py`): status code; media type; JSON shape. The shape is field names and types, with `null` as its own type so null vs. missing vs. wrong type are distinct. Arrays: equal length, and the union of the element shapes (so one element with `null` and another with a string is one shape). Field order, generated ids, timestamps and other success-body values are **not** compared. **Error bodies (status >= 400) are also compared by value**, except the values of volatile keys (`timestamp`, `traceId`, ...; their presence and type are still compared in every body), because error messages are part of the contract. Plain-text success bodies (public key, health) are compared by presence and media type only.
- **Missing routes are never hidden**: Rust answering 404/405 where Java does not is the kind `missing_route`, but only if the Rust body is empty or lacks the application's error fields (`errorCode`/`message`/`fieldErrors`). A 404 with the application error body (resource missing) is a `status_mismatch`. The allowlist refuses to match it, so a missing endpoint fails the run until its ticket is merged.
- **Allowlist** (`contract-tests/allowlist.json`): entries `{step, kind, path, reason}` with glob patterns for step and path; an entry without a non-empty reason, or with unknown keys, makes the run fail with exit code 2. Entries that match nothing are printed as notes so stale ones are removed. A second section, `rust_only`, lists Rust routes without a Java counterpart (`GET /api/v2/me`, `GET /health`), also with reasons.
- **Coverage mode** (`--coverage`): the endpoint list is taken from the Java sources (JAX-RS `@Path` + verb annotations; Quarkus built-ins `/metrics` and `/q/health/{live,ready}` are added by hand) and from the Rust `.route(...)` calls. Path parameter names are normalised to `{}`. It fails when a Java endpoint has no step or a Rust route has no Java counterpart and no `rust_only` entry. This is the acceptance check for "covers every endpoint".
- **Exit codes**: 0 all equal, 1 mismatch (or coverage gap), 2 harness error (unreachable backend, invalid allowlist, no token).
- **CI**: `.github/workflows/contract-tests.yml` runs the unit tests and `--coverage` on changes to `contract-tests/`, `backend/**/presentation` and `rust-backend/src`. The live comparison needs both backends and Keycloak and is run locally or by #137, not on this pipeline.

## Consequences

- Both backends mutate their database during a run (the cleanup steps remove what the scenario creates, but e.g. settings and the admin befriend step persist). Run against fresh databases for reproducible results.
- The Java source parsing is regex-based. A resource that builds paths dynamically would be missed; `--coverage` would then report the count mismatch only if the endpoint is not found at all, so new resource styles need a look at `parity/endpoints.py`.
- Pairs of steps in the scenario run against shared users, so the scenario relies on a second seeded user (`CONTRACT_OTHER_USER_ID`, default `oidc-user-2`).
