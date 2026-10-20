---
title: ADR-2026-10-13-013: Vendored OpenSSL for the static musl Rust image
date: 2026-10-13
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

The `docker-build` job of Rust Backend CI failed on `main` (#133):
`cargo build --release --target x86_64-unknown-linux-musl --locked` could not build `openssl-sys`. `cargo tree -i openssl-sys` shows two paths: `web-push` -> `isahc` -> `curl`/`curl-sys` and `web-push` -> `ece` -> `openssl`. The builder image only has glibc OpenSSL headers, which cannot be linked into a musl binary.

The runtime image is `scratch` (no libc, no shell). The goal is a memory footprint below 50 MiB per pod.

Numbering: `kb/adr/` already contains two `0012-*` files from the old scheme. New ADRs use `ADR-YYYY-MM-DD-NNN-name.md`; the number 013 is the next free one after 011/012 and is deliberately not given a `0013-` prefix, to avoid extending the colliding legacy numbering.

## Decision

Keep the static musl + `scratch` image and **vendor OpenSSL**: `Cargo.toml` adds `openssl = { version = "0.10", features = ["vendored"] }`, which makes `openssl-sys` compile OpenSSL from source (`openssl-src`) against musl.

The builder stage installs `musl-tools` (musl-gcc), `perl` and `make`, and copies `migrations/` (needed by `sqlx::migrate!` at compile time).

### CA bundle in the runtime image

Outbound TLS (web-push services via isahc/curl, Keycloak via reqwest) needs a trust store. `scratch` has none, so the Dockerfile copies `/etc/ssl/certs/ca-certificates.crt` from the builder into the same path in the final image. This relies on vendored OpenSSL, curl and reqwest finding the bundle at that default path. It adds a runtime trust-store dependency to a previously dependency-free image: the bundle is frozen at build time, so the image must be rebuilt to pick up CA updates.

## Alternatives considered

| Option | Result |
|---|---|
| glibc runtime (`debian-slim`/`distroless`) with dynamic OpenSSL | Works, but the image grows to roughly 80-120 MB and carries glibc/libssl that need OS patching; glibc malloc adds memory overhead. |
| Different web-push client without OpenSSL (rustls based) | Cleanest long term, but means replacing/maintaining the `web-push` crate's encryption (`ece` needs OpenSSL) and is out of scope for a CI fix. |
| System OpenSSL cross-compiled for musl | Fragile, needs a custom musl OpenSSL build outside Cargo. |
| **Vendored OpenSSL on musl (chosen)** | Smallest change, keeps the static `scratch` image. |

## Measured result

Built locally with `docker build` of `rust-backend/Dockerfile`, run against PostgreSQL 16:

- Image size: **14.8 MB** (binary + CA bundle).
- Memory: **~1.7 MiB** RSS right after startup and migrations, far below the 50 MiB goal.
- `/q/health/live` and `/q/health/ready` return `200 OK`.

## Consequences

- Longer builds (OpenSSL is compiled from source, a few minutes on a cold cache); the Docker layer/CI cache mitigates this.
- OpenSSL security fixes arrive only through new `openssl-src` releases, so `cargo update -p openssl-src` / dependency bot and `cargo deny` advisories must be tracked and the image rebuilt.
- Builder needs `musl-tools`, `perl` and `make`.
- The image depends on the builder's `ca-certificates.crt` (see above).
- Related: `ADR-2026-10-03-012` (`0012-rust-ci-quality-gates.md`) describes the CI docker-build step; this ADR defines its build prerequisites.
