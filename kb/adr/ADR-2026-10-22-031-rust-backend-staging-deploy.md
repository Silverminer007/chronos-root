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
- Rust uses its own database `chronos_rust` in the existing PostgreSQL pod; the Flyway schema of Java in `chronos` stays untouched. Init scripts only run on an empty volume, so `postgresql.ensureDatabases` adds a post-install/upgrade Job that creates the database idempotently.
- No new Bitwarden secrets: `DATABASE_URL` is composed from `database_password` via `$(DB_PASSWORD)`; Keycloak URL and realm are plain env values like on the Java backend.

## Consequences

- Staging data starts empty for Rust; Java's staging data is not migrated.
- The `RUST_BACKEND_DEPLOY` variable is no longer read and can be deleted.
- Rolling Rust out to production needs its own decision and a chart change on `values-prod.yaml`.
