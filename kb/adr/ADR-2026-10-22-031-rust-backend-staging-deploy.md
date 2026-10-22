---
title: ADR-2026-10-22-031: Rust backend replaces the Java backend on staging
date: 2026-10-22
status: Accepted
supercedes: ADR-2026-10-20-018-rust-deploy-opt-in.md (deploy gate and prod dispatch)
superceded_by: (none)
---

## Context

`develop` did not exist on the remote, so no staging deployment could run. The Rust backend was wired to `deploy-staging`, but behind the repository variable `RUST_BACKEND_DEPLOY` and without an image override in `deployment-ci.yml`. Its staging values also referenced secret keys (`keycloak_url`, `keycloak_realm`, `database_url`) that `chronos-secret` does not contain.

## Decision

- `develop` is created from `main`. Pushes to it deploy staging; `main` keeps deploying production with the Java backend and the Nuxt frontend. Production never receives a Rust dispatch (the production step in `backend-rust-test.yml` is removed).
- On staging the Rust backend replaces Java: `backend.enabled: false` (new chart flag, default `true`, so production renders unchanged) and the Nuxt frontend points `NUXT_QUARKUS_URL` at `chronos-rust-backend`.
- `deployment-ci.yml` writes `rustBackend.image.{tag,repository}` for `service: rust-backend`.
- Rust keeps using the existing staging database `chronos`, so the data stays. Its baseline migration `001` would fail on the Flyway schema, therefore `DATABASE_ADOPT_JAVA_SCHEMA=true` (new, default off) makes `database::adopt_java_schema` run before the migrations: if `appointment` exists, `_sqlx_migrations` has no rows and the schema equals `schema/java_schema_expected.txt` exactly, `001` is recorded as applied with the checksum of the embedded migration (in one transaction under an advisory lock). Then only the Rust-only migrations (`002`) run. A deviating schema aborts the start without changing anything. `001` stays untouched (its checksum is already recorded in other databases). A failed earlier start leaves `_sqlx_migrations` empty, which counts as not managed.
- No new Bitwarden secrets: `DATABASE_URL` is composed from `database_password` via `$(DB_PASSWORD)`; Keycloak URL and realm are plain env values like on the Java backend.

## Consequences

- Java is switched off on staging, so no two backends write to `chronos`. Going back to Java afterwards leaves the extra tables `events` and `_sqlx_migrations`, which Java ignores; take a database backup before the first rollout.
- The `RUST_BACKEND_DEPLOY` variable is no longer read and can be deleted.
- The same flag serves the later production cutover. Rolling Rust out to production needs its own decision and a chart change on `values-prod.yaml`.
