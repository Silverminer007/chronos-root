---
id: KT-005
title: Nachbarsystem PostgreSQL
status: entwurf
typ: nachbarsystem
quellen:
  - backend/src/main/resources/application.properties
  - deployment/templates/postgresql.yaml
  - deployment/values.yaml
  - backend/docker-compose.yaml
verknuepft: [BB-018, KZ-006, VT-002, VT-005]
---

# Nachbarsystem PostgreSQL

- **Rolle:** Einzige Persistenz der Anwendung; das Schema verwaltet Flyway ([KZ-006](../08-konzepte/KZ-006-flyway-migrationen.md)), das Backend migriert beim Start (`quarkus.flyway.migrate-at-start`).
- **Zugriff:** Hibernate/Panache über JDBC; Verbindung `DB_URL`, `DB_USER`, `DB_PASSWORD` (im Profil `dev` mit Standardwerten für `localhost:5432/chronos`); Agroal-Pool mit 10 bis 50 Verbindungen, Wartezeit 30 s, Lebensdauer 15 min.
- **Betrieb:** Im Chart läuft PostgreSQL 16 als eigenes `Deployment` mit `PersistentVolumeClaim` ([VT-002](../07-verteilung/VT-002-kubernetes-ressourcen.md)); lokal startet `backend/docker-compose.yaml` PostgreSQL 18 ([VT-007](../07-verteilung/VT-007-lokale-entwicklungsumgebung.md)). Sicherung: [KT-006](KT-006-s3-backup.md).
