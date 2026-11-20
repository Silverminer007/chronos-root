---
id: VT-005
title: Backup und Restore
status: entwurf
typ: verteilung
quellen:
  - deployment/templates/postgres-backup.yaml
  - deployment/templates/postgres-backup-restore.yaml
  - deployment/restore.sh
verknuepft: [KT-006, QA-006, KZ-006]
---

# Backup und Restore

**Backup:** CronJob `postgres-backup` (`0 2,8,14,20 * * *`, also alle sechs Stunden, `concurrencyPolicy: Forbid`, 7 erfolgreiche und 3 fehlgeschlagene Jobs bleiben). Der Init-Container (`postgres:<tag>`) wartet auf die Datenbank und erzeugt `pg_dump --format=custom --compress=9` nach `/backup/chronos-backup-<Zeitstempel>.sql` (emptyDir bis 5Gi); der Hauptcontainer (`amazon/aws-cli`) lädt die Datei nach `s3://<bucket>/<s3_path>/` hoch. Schlägt ein Schritt fehl, scheitert der Job (`restartPolicy: OnFailure`). Aktiv mit `postgresql.backup.enabled` (Standard `true`).

**Restore:** CronJob `postgres-restore-trigger` ist `suspend: true` mit Zeitplan 31. Februar und läuft nie von selbst. `restore.sh` fragt Namespace und die Bestätigung `YES_I_WANT_TO_RESTORE` ab und erzeugt daraus per `kubectl create job --from=cronjob/…` einen Job: ein Init-Container lädt die zuletzt (nach Dateiname sortiert) abgelegte Sicherung aus S3, der Hauptcontainer trennt alle Verbindungen, löscht und erstellt die Datenbank neu und spielt die Sicherung mit `pg_restore` ein (Laufzeitgrenze 1 h). Das Skript löscht den Job danach. Weder Template noch Skript halten die Anwendungs-Pods an; sie verlieren während des Restore ihre Datenbankverbindungen. Flyway prüft beim nächsten Start den Migrationsstand ([KZ-006](../08-konzepte/KZ-006-flyway-migrationen.md)). Ziele für RPO und RTO sind nicht festgelegt; der Sechs-Stunden-Takt begrenzt den Datenverlust faktisch auf diese Zeitspanne.
