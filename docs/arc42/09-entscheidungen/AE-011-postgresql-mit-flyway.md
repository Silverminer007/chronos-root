---
id: AE-011
title: "PostgreSQL mit Flyway"
status: entwurf
typ: architekturentscheidung
datum: 2025-09-28
quellen:
  - backend/src/main/resources/application.properties
  - backend/src/main/resources/db/migration/
  - backend/src/test/resources/application.properties
  - deployment/templates/postgresql.yaml
  - kb/adr/ADR-2025-09-28-008-postgres-flyway.md (Git-Historie, Begründung und Alternativen)
verknuepft: [AE-012, KZ-006, R-006]
---

# AE-011: PostgreSQL mit Flyway

**Entscheidung:** Die Daten liegen in PostgreSQL 16 (Cluster-Deployment, Volume 10 Gi). Das Schema entwickelt Flyway weiter (`db/migration/V<major>.<minor>.<patch>__*.sql`, aktuell V1.0.0 bis V2.1.0); es migriert beim Start (`migrate-at-start`, `baseline-on-migrate`, Baseline 1.0.0). Der Connection-Pool (Agroal) hat 10 bis 50 Verbindungen.

**Begründung (laut früherem ADR):** ACID-Transaktionen und relationale Abfragen über Termine, Teilnahmen und Gruppen; versionierte, reviewbare Schemaänderungen.

**Alternativen (laut ADR):** MySQL mit Liquibase, MongoDB, SQLite.

**Konsequenzen:**
- Integrationstests laufen gegen echtes PostgreSQL (Quarkus Dev Services mit Testcontainers; Flyway lädt zusätzlich `db/testdata`), nicht gegen H2.
- Migrationen laufen nur vorwärts; Fehler beim Start verhindern den Pod-Start. Beispiel V2.0.0: Die Tabelle `users` entfiel, alle Verweise zeigen auf die Keycloak-`oidcId` ([AE-049](AE-049-lokaler-profil-cache-hinter-identityport.md)).
- Eine einzelne PostgreSQL-Instanz ist der einzige Zustandsspeicher ([R-006](../11-risiken/R-006-datenbank-ist-einziger-zustandsspeicher.md)).
