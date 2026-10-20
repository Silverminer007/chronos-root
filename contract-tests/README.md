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

### Seed prerequisites

The harness needs **no SQL seed rows**. Both databases only need the schema from their own migrations
(Java: Flyway `backend/src/main/resources/db/migration`, run at startup, **without** the test fixtures
`backend/src/test/resources/db/testdata`; Rust: `rust-backend/migrations`, `001_baseline.sql` and following). The
databases must be empty (fresh), because the scenario creates groups, appointments, messages, push subscriptions and friendship
requests through the API and relies on there being no earlier data. There is no `users` table in either backend
(identity is the Keycloak subject), so the "seed users" live in the shared Keycloak realm, not in the databases:

| Prerequisite | Where | Used by |
|---|---|---|
| Test user (token in `CONTRACT_USER_TOKEN`) | Keycloak | all steps except `ROLE_ANON` |
| Second user whose Keycloak subject is `CONTRACT_OTHER_USER_ID` (default `oidc-user-2`) | Keycloak | group member, participant, role and friendship steps |
| Admin user with role `ADMIN_API` (token in `CONTRACT_ADMIN_TOKEN`) | Keycloak | `/api/v2/admin/*` steps (without a token they run as the test user and only compare the 403, so the admin endpoints are not verified) |
| Friendships, groups, appointments | not needed | created by the scenario |

Both backends must be configured for this one realm, so the same token is the same user on both sides.

`CONTRACT_REDIRECT_URI` (option `--redirect-uri`, default `https://chronos.example.org/`) is the redirect URI sent to
`GET /api/v2/user/link/{provider}`; it only needs to be accepted by both backends.

Instead of tokens, set `CONTRACT_TOKEN_URL`, `CONTRACT_CLIENT_ID`, optionally `CONTRACT_CLIENT_SECRET`
and `CONTRACT_USER`/`CONTRACT_PASSWORD` (and `CONTRACT_ADMIN_USER`/`CONTRACT_ADMIN_PASSWORD`) to fetch them.

Exit code 0: no mismatch. 1: mismatch. 2: harness error (backend unreachable, invalid or missing allowlist file, no token).
A route that Rust does not serve yet shows up as `missing_route` (Rust 404/405 where Java answers, with an empty body or one without the application's error fields
`errorCode`/`message`/`fieldErrors`) and cannot be allowlisted. A 404 carrying the application's error body
(route exists, resource missing) is a normal `status_mismatch` and can be allowlisted.

Arrays that are empty on one or both sides cannot be compared element by element. The run stays green but
prints `UNCHECKED <step> ... <path>  beide leer, Form ungeprüft` (or `Java leer` / `Rust leer`) and counts them in
the summary, so a green run with empty lists is not mistaken for verified element shapes.

## Coverage check

```bash
python3 -m parity --coverage
```

Lists Java endpoints (from the JAX-RS resources) without a request definition in `parity/scenario.py`
and Rust routes without a Java counterpart; exit 1 if either is non-empty.

## Allowlist

`allowlist.json`: `entries` (`step`, `kind`, `path` as glob patterns, and a mandatory `reason`) and
`rust_only` (`route`, `reason`) for deliberate Rust-only routes.

Allowlist `step` patterns are `fnmatch` globs on the step names in `parity/scenario.py` (the names are therefore part of
the allowlist's API: rename a step and its entries together); `path` is a glob on the diff path.

## Fixed constants and authentication

The scenario uses fixed placeholders: `provider=google`, `unknown_provider=does-not-exist`,
`missing_user=does-not-exist`, `missing_id=999999999`, so that "not found" steps hit the same missing resource on both
backends. Steps with `ROLE_ANON` (health, metrics, unauthenticated request) are sent without an `Authorization` header;
all other steps carry the user token (admin steps the admin token).

## Tests

```bash
python3 -m unittest discover -s . -t .
```
