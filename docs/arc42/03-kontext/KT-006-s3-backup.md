---
id: KT-006
title: Nachbarsystem S3-Backup
status: entwurf
typ: nachbarsystem
quellen:
  - deployment/templates/postgres-backup.yaml
  - deployment/values-prod.yaml
verknuepft: [VT-005, RA-009]
---

# Nachbarsystem S3-Backup

- **Rolle:** Externer S3-kompatibler Objektspeicher für Datenbanksicherungen.
- **Schnittstelle:** Der CronJob `postgres-backup` lädt die Sicherung mit der AWS-CLI hoch (`aws s3 cp` nach `s3://<bucket>/<s3_path>/`). Zugang über die Secret-Schlüssel `backup_bucket`, `backup_access_key_id`, `backup_secret_access_key`, `backup_region`, `backup_endpoint_url`; Präfix `postgresql.backup.s3_path` (Produktion `chronos-prod-backup`).
- **Schalter:** `postgresql.backup.enabled` (Standard `true`). Ablauf: [VT-005](../07-verteilung/VT-005-backup-restore.md).
