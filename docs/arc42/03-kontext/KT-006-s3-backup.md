---
id: KT-006
title: Nachbarsystem S3-Backup
status: entwurf
typ: nachbarsystem
quellen:
  - deployment/templates/postgres-backup.yaml
  - deployment/templates/postgres-backup-restore.yaml
  - deployment/restore.sh
verknuepft: [VT-005]
---

# Nachbarsystem S3-Backup

- **Rolle:** Externer S3-kompatibler Speicher für Datenbanksicherungen.
- **Schnittstelle:** CronJob `postgres-backup` (`0 2,8,14,20 * * *`, alle 6 Stunden, `pg_dump` im Init-Container, Upload zum S3). Zugangsdaten (`bucket`, `access_key_id`, `secret_access_key`, `region`, `endpoint_url`) kommen aus dem Secrets-Provider.
- **Optional:** `postgresql.backup.enabled` (Standard `true`; in der Parity-Umgebung aus).
