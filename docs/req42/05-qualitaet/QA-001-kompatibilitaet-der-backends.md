---
id: QA-001
title: Kompatibilität der Backends
status: entwurf
typ: qualitaetsanforderung
quellen: [contract-tests/README.md, kb/adr/ADR-2026-10-20-021-contract-parity-harness.md]
verknuepft: []
---

# QA-001: Kompatibilität der Backends

**Qualitätsmerkmal:** Kompatibilität

## Szenario
Rust- und Java-Backend liefern dieselbe API; der Contract-Harness weist keine unerwarteten Abweichungen aus (Ausnahmen stehen in `contract-tests/allowlist.json`).

## Messbar durch
`contract-tests/`, Contract-Parity-Workflow.
