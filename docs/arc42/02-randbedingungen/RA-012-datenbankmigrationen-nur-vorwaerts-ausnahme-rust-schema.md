---
id: RA-012
title: "Datenbankmigrationen nur vorwärts, Ausnahme Rust-Schema"
status: entwurf
typ: randbedingung
quellen:
  - kb/constraints/C2025-09-28-004-forward-migrations.md
  - kb/adr/ADR-2026-10-12-014-rust-schema-identical-to-java.md
verknuepft: [RB-003]
---

# RA-012: Datenbankmigrationen nur vorwärts, Ausnahme Rust-Schema

Konkretisierung von RB-003: Flyway-Migrationen `V<major>.<minor>.<patch>__beschreibung.sql` sind nach dem Deployment unveränderlich. Die Ausnahme für das Rust-Schema (identisch zum Java-Schema) ist im ADR zu AE "Rust schema identical to Java" beschrieben.
