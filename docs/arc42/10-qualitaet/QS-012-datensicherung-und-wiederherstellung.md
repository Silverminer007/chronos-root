---
id: QS-012
title: "Datensicherung und Wiederherstellung"
status: entwurf
typ: qualitaetsszenario
quellen:
  - deployment/templates/postgres-backup.yaml
  - deployment/templates/postgres-backup-restore.yaml
  - deployment/restore.sh
verknuepft: [QA-006, R-006]
---

# QS-012: Datensicherung und Wiederherstellung

| | |
|---|---|
| **Reiz** | Die Datenbank geht verloren oder wird beschädigt. |
| **Antwort** | Ein CronJob (`postgres-backup`, 02, 08, 14 und 20 Uhr) erstellt mit `pg_dump` ein Backup und lädt es nach S3 hoch. `deployment/restore.sh` spielt nach Namespace-Abfrage das neueste Backup ein (löscht die bestehende Datenbank). |
| **Messung** | Datenverlust höchstens ein Backup-Intervall von 6 h (RPO) bei erfolgreichem Lauf; die Wiederherstellungszeit (RTO) ist nicht festgelegt **(?)**. Lokal werden Dumps nach 7 Tagen gelöscht; der CronJob behält 7 erfolgreiche und 3 fehlgeschlagene Läufe. |
| **Nachweis** | Chart-Templates; ein Wiederherstellungstest und ein Alarm bei fehlgeschlagenem Backup sind nicht dokumentiert **(?)**. |
