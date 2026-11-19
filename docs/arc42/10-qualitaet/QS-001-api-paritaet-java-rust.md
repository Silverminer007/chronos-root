---
id: QS-001
title: "API-Parität Java/Rust"
status: entwurf
typ: qualitaetsszenario
quellen:
  - contract-tests/
  - .github/workflows/contract-tests.yml
  - kb/adr/ADR-2026-10-20-021-contract-parity-harness.md
verknuepft: [QA-001]
---

# QS-001: API-Parität Java/Rust

**Szenario:** Ein Request läuft gegen das Java- und das Rust-Backend. **Erwartung:** Status und JSON-Antwort stimmen überein, außer die Abweichung steht mit Grund in `contract-tests/allowlist.json`. **Nachweis:** Harness `python3 -m parity` (CI-Workflow `contract-tests.yml`), Abschlussläufe in ARCH-Dokumenten von `kb/architecture/ARCH-2026-10-*`.
