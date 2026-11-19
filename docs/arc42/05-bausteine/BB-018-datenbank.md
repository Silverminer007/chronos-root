---
id: BB-018
title: Datenbank (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - backend/src/main/resources/db/migration
verknuepft: [KT-005, KZ-006, KZ-008]
---

# Datenbank (Ebene 1)

PostgreSQL mit Flyway-Migrationen `V1.0.0` (Initialschema) bis `V2.1.0` (`user_profiles`). Wichtige Schritte: `V1.1.0` neues Terminsystem, `V1.6.0` Push-Log, `V2.0.0` Entfernen der `users`-Tabelle (Identität liegt in Keycloak), `V2.1.0` `user_profiles`. Das Rust-Backend verwendet dasselbe Schema (ADR-014).
