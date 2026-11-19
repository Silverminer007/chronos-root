---
id: KZ-006
title: Datenbankmigrationen mit Flyway
status: entwurf
typ: konzept
quellen:
  - kb/constraints/C2025-09-28-004-forward-migrations.md
  - backend/src/main/resources/db/migration
verknuepft: [RB-003, BB-018, LS-007]
---

# Datenbankmigrationen mit Flyway

Dateiname `V<major>.<minor>.<patch>__beschreibung.sql`; ausgelieferte Migrationen werden nie geändert oder gelöscht, Fehler korrigiert eine neue Migration. Das Rust-Backend teilt das Schema (ADR-014, ADR-012 zur Konfiguration/Migration im Rust-Backend). Datenmigrationen, die Benutzerdaten betreffen, sind sicherheitsrelevant (Orchestrator gibt sie nicht automatisch frei).
