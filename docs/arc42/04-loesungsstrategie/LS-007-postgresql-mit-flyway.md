---
id: LS-007
title: PostgreSQL mit Flyway, nur vorwärts
status: entwurf
typ: strategie
quellen:
  - kb/adr/ADR-2025-09-28-008-postgres-flyway.md
  - kb/adr/ADR-2025-09-28-009-panache.md
verknuepft: [RB-003, KZ-006]
---

# PostgreSQL mit Flyway, nur vorwärts

Eine relationale Datenbank, Schema ausschließlich über versionierte Flyway-Migrationen, die nach Deployment unveränderlich sind. Java nutzt Hibernate ORM mit Panache; Tests laden Fixtures aus `src/test/resources/db/testdata/`.
