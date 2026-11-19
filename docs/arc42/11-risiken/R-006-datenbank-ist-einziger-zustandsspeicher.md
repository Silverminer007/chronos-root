---
id: R-006
title: "Datenbank ist einziger Zustandsspeicher"
status: entwurf
typ: risiko
quellen:
  - deployment/templates/postgres-backup.yaml
  - deployment/templates/postgres-backup-restore.yaml
verknuepft: []
---

# R-006: Datenbank ist einziger Zustandsspeicher

PostgreSQL läuft im Cluster mit Backup nach S3 (`postgres-backup.yaml`, Restore-Skript `restore.sh`). Wiederherstellungstests und Zielwerte (RPO/RTO) sind nicht dokumentiert **(?)**.
