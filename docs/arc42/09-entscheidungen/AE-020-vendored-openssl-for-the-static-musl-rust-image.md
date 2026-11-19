---
id: AE-020
title: "Vendored OpenSSL for the static musl Rust image"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-13
quellen:
  - kb/adr/ADR-2026-10-13-013-vendored-openssl-musl-image.md
verknuepft: []
---

# AE-020: Vendored OpenSSL for the static musl Rust image

Quelle: `kb/adr/ADR-2026-10-13-013-vendored-openssl-musl-image.md` (Status im ADR: accepted, Datum: 2026-10-13). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

The `docker-build` job of Rust Backend CI failed on `main` (#133):
`cargo build --release --target x86_64-unknown-linux-musl --locked` could not build `openssl-sys`. `cargo tree -i openssl-sys` shows two paths: `web-push` -> `isahc` -> `curl`/`curl-sys` and `web-push` -> `ece` -> `openssl`. The builder image only has glibc OpenSSL headers, which cannot be linked into a musl binary.

The runtime image is `scratch` (no libc, no shell). The goal is a memory footprint below 50 MiB per pod.

Numbering: `kb/adr/` already contains two `0012-*` files from the old scheme. New ADRs use `ADR-YYYY-MM-DD-NNN-name.md`; the number 013 is the next free one after 011/012 and is deliberately not given a `0013-` prefix, to avoid extending the colliding legacy numbering.

## Entscheidung

Keep the static musl + `scratch` image and **vendor OpenSSL**: `Cargo.toml` adds `openssl = { version = "0.10", features = ["vendored"] }`, which makes `openssl-sys` compile OpenSSL from source (`openssl-src`) against musl.

The builder stage installs `musl-tools` (musl-gcc), `perl` and `make`, and copies `migrations/` (needed by `sqlx::migrate!` at compile time).

### CA bundle in the runtime image

Outbound TLS (web-push services via isahc/curl, Keycloak via reqwest) needs a trust store. `scratch` has none, so the Dockerfile copies `/etc/ssl/certs/ca-certificates.crt` from the builder into the same path in the final image. This relies on vendored OpenSSL, curl and reqwest finding the bundle at that default path. It adds a runtime trust-store dependency to a previously dependency-free image: the bundle is frozen at build time, so the image must be rebuilt to pick up CA updates.

## Konsequenzen

- Longer builds (OpenSSL is compiled from source, a few minutes on a cold cache); the Docker layer/CI cache mitigates this.
- OpenSSL security fixes arrive only through new `openssl-src` releases, so `cargo update -p openssl-src` / dependency bot and `cargo deny` advisories must be tracked and the image rebuilt.
- Builder needs `musl-tools`, `perl` and `make`.
- The image depends on the builder's `ca-certificates.crt` (see above).
- Related: `ADR-2026-10-03-012` (`0012-rust-ci-quality-gates.md`) describes the CI docker-build step; this ADR defines its build prerequisites.
