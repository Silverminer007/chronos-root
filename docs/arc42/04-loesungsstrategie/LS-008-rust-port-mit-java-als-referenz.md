---
id: LS-008
title: Rust-Backend als paritätsgleicher Port
status: entwurf
typ: strategie
quellen:
  - kb/adr/ADR-2026-10-14-014-rust-schema-identical-to-java.md
  - kb/adr/ADR-2026-10-20-021-contract-parity-harness.md
  - kb/adr/ADR-2026-10-20-018-rust-deploy-opt-in.md
verknuepft: [QA-001, BB-007, BB-010, VT-004]
---

# Rust-Backend als paritätsgleicher Port

Das Rust-Backend (axum, sqlx) re-implementiert die Java-API 1:1 auf demselben Schema; Java ist die Referenz. Parität wird über einen Contract-Harness mit Allowlist bewiesen (ADR-021, ARCH-2026-10-20-001/-002). Das Deployment des Rust-Images ist per Repository-Variable opt-in. Ob Rust Java langfristig ersetzt, ist **(?)** offen.
