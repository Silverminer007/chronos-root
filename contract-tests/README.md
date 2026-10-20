# Contract parity harness

Sends the same requests to the Java backend (`backend/`, reference) and the Rust backend
(`rust-backend/`) and diffs the responses: status code, media type, JSON field names, types,
`null` vs. missing and error bodies. Field order, generated ids and timestamps are ignored.
Design: [ADR-2026-10-20-021](../kb/adr/ADR-2026-10-20-021-contract-parity-harness.md).

Python 3.9+, standard library only.

## Run

Start both backends against fresh databases (Java: Flyway migrations, Rust: `rust-backend/migrations`),
both configured for the same Keycloak, then from this directory:

```bash
export CONTRACT_JAVA_URL=http://localhost:8080
export CONTRACT_RUST_URL=http://localhost:8081
export CONTRACT_USER_TOKEN=<access token of the test user>
export CONTRACT_ADMIN_TOKEN=<access token of a user with role ADMIN_API>   # optional
export CONTRACT_OTHER_USER_ID=<oidc id of a second existing user>            # default oidc-user-2
python3 -m parity
```

Instead of tokens, set `CONTRACT_TOKEN_URL`, `CONTRACT_CLIENT_ID`, optionally `CONTRACT_CLIENT_SECRET`
and `CONTRACT_USER`/`CONTRACT_PASSWORD` (and `CONTRACT_ADMIN_USER`/`CONTRACT_ADMIN_PASSWORD`) to fetch them.

Exit code 0: no mismatch. 1: mismatch. 2: harness error (backend unreachable, invalid allowlist, no token).
A route that Rust does not serve yet shows up as `missing_route` (Rust 404/405 where Java answers) and
cannot be allowlisted.

## Coverage check

```bash
python3 -m parity --coverage
```

Lists Java endpoints (from the JAX-RS resources) without a request definition in `parity/scenario.py`
and Rust routes without a Java counterpart; exit 1 if either is non-empty.

## Allowlist

`allowlist.json`: `entries` (`step`, `kind`, `path` as glob patterns, and a mandatory `reason`) and
`rust_only` (`route`, `reason`) for deliberate Rust-only routes.

## Tests

```bash
python3 -m unittest discover -s . -t .
```
