---
id: LS-007
title: PostgreSQL mit Flyway, nur vorwärts
status: entwurf
typ: strategie
quellen:
  - backend/pom.xml
  - backend/src/main/resources/application.properties
verknuepft: [AE-011, AE-012, BB-018, KZ-006]
---

# PostgreSQL mit Flyway, nur vorwärts

Eine relationale Datenbank, Schema ausschließlich über versionierte Flyway-Migrationen, die nach dem Deployment unveränderlich sind (KZ-006). Das Backend nutzt Hibernate ORM mit Panache und migriert beim Start. Tests laufen gegen echtes PostgreSQL mit Fixtures ([KZ-010](../08-konzepte/KZ-010-teststrategie.md)). Begründungen: [AE-011](../09-entscheidungen/AE-011-postgresql-mit-flyway.md), [AE-012](../09-entscheidungen/AE-012-hibernate-panache-als-persistenz-api.md).
