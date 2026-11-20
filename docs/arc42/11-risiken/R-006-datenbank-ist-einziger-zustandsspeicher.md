---
id: R-006
title: "Datenbank ist einziger Zustandsspeicher"
status: entwurf
typ: risiko
quellen:
  - deployment/templates/postgresql.yaml
  - deployment/templates/postgres-backup.yaml
  - deployment/templates/postgres-backup-restore.yaml
  - deployment/restore.sh
verknuepft: [QA-006, AE-011, QS-012]
---

# R-006: Datenbank ist einziger Zustandsspeicher

**Risiko:** PostgreSQL läuft als einzelner Pod (`replicas: 1`, PVC 10 Gi, StorageClass `hcloud-volumes`) im Cluster, ohne Replikation. Jeder Ausfall des Pods oder Volumes unterbricht die gesamte Anwendung; Datenverlust droht bis zum letzten Backup.

**Auswirkung:** Ausfall bis zur Wiederherstellung; Verlust von bis zu 6 h an Daten ([QS-012](../10-qualitaet/QS-012-datensicherung-und-wiederherstellung.md)).

**Maßnahmen:** Backup alle 6 h nach S3 und `restore.sh`. Ein Wiederherstellungstest, Zielwerte (RPO/RTO) und eine Überwachung fehlgeschlagener Backups sind nicht dokumentiert **(?)**.
