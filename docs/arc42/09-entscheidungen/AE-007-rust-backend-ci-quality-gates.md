---
id: AE-007
title: "Rust Backend CI Quality Gates"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: (?)
quellen:
  - kb/adr/0012-rust-ci-quality-gates.md
verknuepft: []
---

# AE-007: Rust Backend CI Quality Gates

Quelle: `kb/adr/0012-rust-ci-quality-gates.md` (Status im ADR: accepted, Datum: (?)). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

The Rust backend implementation (#21) required comprehensive CI/CD quality gates to match production readiness. The existing workflow was incomplete and the release build was broken (non-existent `web` feature).

**Requirements:**
- Every PR touching `rust-backend/` must pass all quality gates before merge
- Static checks (fmt, clippy, docs) run first for fast feedback
- Dynamic checks (unit tests, integration tests, coverage, supply chain) run with testcontainers
- Docker image builds on PRs to catch build failures before merge
- Coverage has a ratchet: never decreases, only increases
- Dependencies monitored for security advisories; ignored advisories documented with reasons

## Entscheidung

Implement **9 separate CI jobs**, each responsible for one quality gate. Jobs run in parallel where possible; docker-build and release build depend on all quality gates.

### The 9 Gates

1. **fmt** — `cargo fmt --check`
   - Detects formatting violations
   - Fastest feedback, no cache needed
   - Required for all PRs

2. **clippy** — `cargo clippy --all-targets --all-features -- -D warnings`
   - Enforces linter warnings as errors
   - Catches logic, style, and performance issues
   - Uses rust-cache for faster rebuilds

3. **test-unit** — `cargo test --lib --locked`
   - Unit tests (no database)
   - Validates business logic in isolation
   - Uses `--locked` to enforce Cargo.lock

4. **test-integration** — `cargo test --test '*' --locked -- --include-ignored`
   - Integration tests with Testcontainers/PostgreSQL
   - Runs ignored tests (performance baselines, long-running tests) …

## Konsequenzen

### Positive

✅ **Comprehensive coverage**: All 9 gates catch different classes of defects  
✅ **Fast feedback**: Static checks (fmt, clippy) run first; unit tests second  
✅ **Reproducible builds**: `--locked` + committed Cargo.lock ensures determinism  
✅ **Security visibility**: cargo-deny tracks all advisories with reasons  
✅ **No regressions**: Coverage ratchet prevents backsliding  
✅ **Docker validation**: Image builds on every PR catch build failures early  
✅ **Known issues tracked**: Ignored advisories documented; remediation planned  
✅ **Clear gate ownership**: Each job has a single responsibility  

### Negative

❌ **Build time**: Full CI runs ~10-15 min (unit + integration + coverage); mitigated by parallelization  
❌ **Testcontainers overhead**: Integration tests require Docker; CI-friendly env variables set …
