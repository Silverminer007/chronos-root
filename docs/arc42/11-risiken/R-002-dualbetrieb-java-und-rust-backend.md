---
id: R-002
title: "Dualbetrieb Java- und Rust-Backend"
status: entwurf
typ: risiko
quellen:
  - deployment/README.md
  - kb/adr/ADR-2026-10-20-018-rust-deploy-opt-in.md
  - Ticket #337
verknuepft: [QA-001]
---

# R-002: Dualbetrieb Java- und Rust-Backend

Zwei Backends müssen gleiches Verhalten zeigen; jede Änderung kostet doppelten Aufwand und kann Parität brechen. Welches Backend neue Features bekommt, ist ungeklärt (Ticket #337, `needs-po`). Maßnahmen: Contract-Harness (QS-001), Rust-Deploy ist opt-in.
