---
id: QA-006
title: Zuverlässigkeit: Datensicherung
status: entwurf
typ: qualitaetsanforderung
quellen: [deployment/templates/postgres-backup.yaml, deployment/restore.sh]
verknuepft: []
---

# QA-006: Zuverlässigkeit: Datensicherung

**Qualitätsmerkmal:** Zuverlässigkeit

## Szenario
PostgreSQL wird gesichert (Backup-Job nach S3) und lässt sich mit `restore.sh` wiederherstellen. **(?)** Ziele für RPO und RTO festlegen.

## Messbar durch
`deployment/templates/postgres-backup*.yaml`, `deployment/restore.sh`.
