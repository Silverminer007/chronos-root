---
title: ADR-2026-10-03-012: Rust Backend CI Quality Gates
date: 2026-10-03
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

The Rust backend implementation (#21) required comprehensive CI/CD quality gates to match production readiness. The existing workflow was incomplete and the release build was broken (non-existent `web` feature).

**Requirements:**
- Every PR touching `rust-backend/` must pass all quality gates before merge
- Static checks (fmt, clippy, docs) run first for fast feedback
- Dynamic checks (unit tests, integration tests, coverage, supply chain) run with testcontainers
- Docker image builds on PRs to catch build failures before merge
- Coverage has a ratchet: never decreases, only increases
- Dependencies monitored for security advisories; ignored advisories documented with reasons

## Decision

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
   - Runs ignored tests (performance baselines, long-running tests)
   - `TESTCONTAINERS_RYUK_DISABLED=true` for CI environments

5. **coverage** — `cargo llvm-cov --all-targets --all-features --workspace --locked -- --include-ignored`
   - Measures line coverage over unit + integration tests
   - Publishes summary to job summary
   - Uploads `lcov.info` artifact for external tools
   - Enforces coverage threshold (see below)

6. **deny** — `cargo deny check advisories bans licenses sources`
   - Detects security advisories in dependency tree
   - Verifies licenses are OSI-approved or explicitly allowed
   - Checks for duplicate/conflicting crate versions (bans)
   - Validates approved registries (sources)
   - Ignores known advisories with documented reasons (see below)

7. **docs** — `cargo doc --no-deps --locked` with `RUSTDOCFLAGS="-D warnings"`
   - Generates and checks documentation
   - Ensures all public API is documented
   - Warnings treated as errors

8. **docker-build** — `docker build` (on PRs only, no push)
   - Validates Dockerfile builds successfully
   - Catches Rust build failures before merge
   - Uses docker/build-push-action for caching

9. **build** (release) — `cargo build --release --target x86_64-unknown-linux-musl --locked`
   - Full release build for `main` and `develop` branches
   - Depends on all 8 quality gates
   - Pushes image to GHCR if all gates pass

### Coverage Threshold & Ratchet

- **Measurement**: Line coverage from unit + integration tests
- **Threshold**: Rounded **down** to the nearest multiple of 5 (minimum 40%)
  - Example: current coverage 73% → threshold 70%; coverage 42% → threshold 40%
- **Ratchet rule**: Threshold is a minimum; never decreases, only increases as coverage improves
- **Enforcement**: Job fails if coverage < threshold
- **Rationale**: Prevents regression while accommodating natural variation in test suite maturity

### Ignored Tests Policy

Integration test files contain tests marked `#[ignore]` for various reasons:

- **Performance baselines** (`performance_baseline_test.rs`) — Long-running profiling tests; marked with reason in comments
- **Unimplemented features** — Tests for features not yet implemented; listed in this ADR as "skip"; not executed in CI
- **All other ignored tests** — Must pass in CI (`--include-ignored` flag); any failures are real bugs

**Ignored tests that are skipped (unimplemented features):**
- None currently; all ignored tests are performance baselines or work-in-progress

### Dependency & Supply Chain

**Dependency Lockfile:**
- `Cargo.lock` is committed to the repository
- All builds use `--locked` to ensure reproducibility
- Dependencies pinned to exact versions; transitive deps managed by Cargo

**Supply Chain Checks (cargo-deny):**
- **Advisories** — Runs with explicit ignores for known issues
- **Licenses** — Allows OSI-approved licenses: MIT, Apache-2.0, Apache-2.0 WITH LLVM-exception, BSD-2-Clause, BSD-3-Clause, ISC, MPL-2.0, BSL-1.0, CC0-1.0, LGPL-2.1-or-later, Unicode-3.0, Unlicense, Zlib

**Ignored Advisories & Reasons:**

| ID | Crate | Reason | Severity |
|---|---|---|---|
| RUSTSEC-2026-0258 | h2 0.3.27 | Low severity unbounded DATA frames; transitive (reqwest → hyper → h2); modern hyper+h2 upgrade planned | Low |
| RUSTSEC-2024-0436 | paste 1.0.15 | Unmaintained; via sqlx-macros (dev-time only); macro_rules migration planned | Unmaintained |
| RUSTSEC-2023-0071 | rsa 0.9.10 | Timing side-channel via jwt-simple (web-push); local-only use; web-push upgrade planned | Medium |
| RUSTSEC-2025-0134 | rustls-pemfile 1.0.4 | Archived but thin wrapper; direct rustls-pki-types migration planned | Unmaintained |
| RUSTSEC-2026-0098, -0099, -0049 | rustls-webpki 0.101.7+0.102.8 | Certificate validation bugs (post-signature verification); low impact; testcontainers/sqlx upgrade planned | Low |
| RUSTSEC-2026-0104 | rustls-webpki 0.102.8 | CRL parsing panic; via testcontainers (dev-only); upgrade planned | Low |
| RUSTSEC-2024-0363 | sqlx 0.7.4 | SQL injection via >4GiB encoding overflow; requires exceptional payload size; input validation enforced; upgrade to 0.8.1+ planned | Medium |

**Remediation Plan:**
- Short term: Keep all ignored advisories and monitor
- Medium term: Upgrade web-push, testcontainers, reqwest, sqlx to close vulnerabilities
- Long term: Pin exact versions with no known advisories

### Release Build Fix

**Issue:** Dockerfile and workflow referenced non-existent `--features web` flag, breaking release builds.

**Fix:**
- Removed `--features web` from both Dockerfile and workflow
- Added `--locked` to enforce Cargo.lock usage
- Build now compiles the library binary exactly as Dockerfile expects

**Verification:** Release build passes `cargo build --release --target x86_64-unknown-linux-musl --locked`

## Consequences

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
❌ **Testcontainers overhead**: Integration tests require Docker; CI-friendly env variables set  
❌ **Advisory noise**: Known vulnerabilities produce warnings; mitigated by clear ignore reasons  
❌ **Coverage maintenance**: Threshold ratchet means coverage must always increase; not decreasing is a feature, not a bug  

### Trade-offs

- **Strictness vs. velocity**: Coverage ratchet prevents backsliding but requires thoughtful test strategy
- **Completeness vs. time**: Running integration tests with `--include-ignored` adds ~2 min; catches real bugs worth the cost
- **Supply chain visibility vs. friction**: Ignored advisories add friction but provide transparency and traceable remediation

## Architecture

```
Pull Request
    ├── fmt (fast, no cache needed)
    ├── clippy (fast, cached)
    ├── test-unit (fast, cached)
    ├── test-integration (slow, Testcontainers)
    ├── coverage (slow, Testcontainers)
    ├── deny (fast, advisory DB)
    ├── docs (medium, cached)
    └── docker-build (medium, cached)
         └─── [All 8 gates pass]
              └─── build (release, only on main/develop)
                   └─── deploy (dispatch to Helm/K8s)
```

## Maintenance

### Adding New Gates

1. Create a new job in `.github/workflows/backend-rust-test.yml`
2. Make it either:
   - Independent (runs in parallel)
   - A dependency of existing gate (add to `needs:`)
3. Update this ADR with gate description and rationale

### Updating Coverage Threshold

1. Edit the threshold calculation in the coverage job
2. Update the "Threshold" section in this ADR
3. Commit with message: "chore: raise coverage threshold to X%"
4. Document in PR that threshold only increases, never decreases

### Handling New Advisories

1. Run `cargo deny check advisories` locally
2. Add new ID to `deny.toml` ignore list with reason and ticket reference
3. Open issue to track remediation (e.g., "upgrade web-push to 0.12+")
4. Update this ADR's advisory table
5. Commit with message: "chore: ignore RUSTSEC-XXXX-XXXX (tracked in #YYY)"

## References

- Rust CLI tooling: https://doc.rust-lang.org/cargo/
- cargo-llvm-cov: https://github.com/taiki-e/cargo-llvm-cov
- cargo-deny: https://github.com/EmbarkStudios/cargo-deny
- GitHub Actions Rust caching: https://github.com/Swatinem/rust-cache
- Testcontainers: https://testcontainers.com/
