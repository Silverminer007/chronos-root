---
id: BB-010
title: Contract-Tests (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - contract-tests/README.md
  - kb/adr/ADR-2026-10-20-021-contract-parity-harness.md
  - deployment/parity
verknuepft: [LS-008, QA-001]
---

# Contract-Tests (Ebene 1)

Python-Harness (`python3 -m parity`), der Java und Rust per URL vergleicht. Eine Allowlist (`allowlist.json`) enthält bekannte, ausschließlich Java-seitige Abweichungen. Treiber in der lokalen k3s-Umgebung: `deployment/parity/run-contract.sh`; explorativer Lauf: `deployment/parity/explore.py`. CI: Workflow `contract-tests.yml`. Belege: ARCH-2026-10-20-001/-002, ARCH-2026-10-22-003.
