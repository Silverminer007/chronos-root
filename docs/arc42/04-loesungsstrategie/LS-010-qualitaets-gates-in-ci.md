---
id: LS-010
title: Automatische Qualitäts-Gates
status: entwurf
typ: strategie
quellen:
  - kb/constraints/001-code-quality-gates.md
  - .github/workflows
verknuepft: [QA-002, VT-004]
---

# Automatische Qualitäts-Gates

Backend: Checkstyle, SpotBugs, PMD bei `./mvnw verify`; Rust: `cargo fmt --check`, `clippy -D warnings`, Unit- und Integrationstests; Frontend: ESLint, Typecheck, `npm audit`, Secret-Detection; Repo: Prüfung auf eingecheckte lokale Agent-Einstellungen. Jeder PR durchläuft diese Prüfungen.
