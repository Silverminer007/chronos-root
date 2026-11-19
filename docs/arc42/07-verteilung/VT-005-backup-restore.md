---
id: VT-005
title: Backup und Restore
status: entwurf
typ: verteilung
quellen:
  - deployment/templates/postgres-backup.yaml
  - deployment/templates/postgres-backup-restore.yaml
  - deployment/restore.sh
verknuepft: [KT-006]
---

# Backup und Restore

Backup alle sechs Stunden per CronJob (`concurrencyPolicy: Forbid`, 7 erfolgreiche und 3 fehlgeschlagene Jobs bleiben). Wiederherstellung über `postgres-backup-restore.yaml` bzw. `restore.sh` **(?)** genauer Ablauf klären. Migrationen sind unveränderlich (RB-003); ein Restore spielt sie bei Bedarf nach.
