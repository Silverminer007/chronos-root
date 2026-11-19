---
id: RA-006
title: "Java ist die Referenz des Rust-Ports"
status: entwurf
typ: randbedingung
quellen:
  - kb/adr/ADR-2026-10-12-014-rust-schema-identical-to-java.md
  - kb/adr/ADR-2026-10-20-021-contract-parity-harness.md
verknuepft: [QA-001]
---

# RA-006: Java ist die Referenz des Rust-Ports

Das Rust-Backend reimplementiert die Java-API (Ticket-Landkarte #21, "Java is the reference"). Das Datenbankschema ist identisch; das Verhalten wird per Contract-Harness (`contract-tests/`) verglichen. Bewusste Abweichungen stehen in `contract-tests/allowlist.json` und in den Parity-ADRs. Ob Java langfristig entfällt, ist offen (Ticket #337, PO-Entscheid) **(?)**.
