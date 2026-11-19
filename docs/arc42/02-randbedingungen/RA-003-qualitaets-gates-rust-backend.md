---
id: RA-003
title: "Qualitäts-Gates Rust-Backend"
status: entwurf
typ: randbedingung
quellen:
  - kb/adr/0012-rust-ci-quality-gates.md
  - .agent-orchestrator/prompts/rules.extra.md
verknuepft: [QA-002]
---

# RA-003: Qualitäts-Gates Rust-Backend

Jeder PR an `rust-backend/` muss die CI-Gates bestehen (u. a. `cargo fmt --check`, `cargo clippy --all-targets -- -D warnings`, Unit-/Integrationstests, Coverage-Ratchet). Tests brauchen PostgreSQL (`TEST_DATABASE_URL`). Details: AE-Eintrag zu "Rust Backend CI Quality Gates" in `../09-entscheidungen/`.
