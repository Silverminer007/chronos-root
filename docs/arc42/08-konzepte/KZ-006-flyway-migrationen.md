---
id: KZ-006
title: Datenbankmigrationen mit Flyway
status: entwurf
typ: konzept
quellen:
  - backend/src/main/resources/application.properties
  - backend/src/main/resources/db/migration
  - backend/src/test/resources/application.properties
verknuepft: [BB-018, LS-007, RA-011]
---

# Datenbankmigrationen mit Flyway

Dateiname `V<major>.<minor>.<patch>__beschreibung.sql` im Verzeichnis `db/migration`; ausgelieferte Migrationen werden nie geändert oder gelöscht, Fehler korrigiert eine neue Migration (nur vorwärts, keine Rollbacks). Das Backend migriert beim Start (`migrate-at-start`) und baselined bestehende Datenbanken auf `1.0.0` (`baseline-on-migrate`). Tests verwenden dieselben Migrationen plus `db/testdata` (`clean-at-start`, [KZ-010](KZ-010-teststrategie.md)); Hibernate verwaltet das Schema nicht (`schema-management.strategy=none` in Tests). Migrationen, die Benutzerdaten verändern (`V2.0.0`), sind sicherheitsrelevant und werden vom Orchestrator nicht automatisch freigegeben ([RA-011](../02-randbedingungen/RA-011-agenten-orchestrierung-und-steward.md)); `V2.0.0` enthält eine Vorabprüfung und eine Backfill-Strategie. Inhalt der Migrationen: [BB-018](../05-bausteine/BB-018-datenbank.md).
