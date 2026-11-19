---
id: KT-005
title: Nachbarsystem PostgreSQL
status: entwurf
typ: nachbarsystem
quellen:
  - deployment/templates/postgresql.yaml
  - backend/src/main/resources/db/migration
verknuepft: [KZ-006, BB-005]
---

# Nachbarsystem PostgreSQL

- **Rolle:** Einzige Persistenz. Schema durch Flyway (`V<major>.<minor>.<patch>__beschreibung.sql`).
- **Zugriff:** Java über Hibernate/Panache (`DB_URL`, `DB_USER`, `DB_PASSWORD`), Rust über sqlx (`DATABASE_URL`, `DATABASE_MAX_CONNECTIONS`, Standard 16).
- **Zusatz im Rust-Backend:** LISTEN/NOTIFY als Event-Bus und Advisory-Lock als Leader-Election (KZ-003, LZ-006).
- **Betrieb:** StatefulSet/Deployment im Chart, Backup per CronJob (KT-006).
