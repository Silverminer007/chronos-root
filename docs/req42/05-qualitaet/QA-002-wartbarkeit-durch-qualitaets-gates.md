---
id: QA-002
title: Wartbarkeit durch Qualitäts-Gates
status: entwurf
typ: qualitaetsanforderung
quellen: [kb/constraints/001-code-quality-gates.md, .agent-orchestrator/prompts/rules.extra.md]
verknuepft: []
---

# QA-002: Wartbarkeit durch Qualitäts-Gates

**Qualitätsmerkmal:** Wartbarkeit

## Szenario
Jeder PR besteht Checkstyle, SpotBugs, PMD bzw. `cargo fmt`/`clippy`, ESLint und Typecheck.

## Messbar durch
CI-Pipelines der Teilprojekte.
