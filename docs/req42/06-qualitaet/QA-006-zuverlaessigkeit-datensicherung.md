---
id: QA-006
title: Zuverlässigkeit: Datensicherung
status: entwurf
typ: qualitaetsanforderung
quellen: [deployment/templates/postgres-backup.yaml, deployment/templates/postgres-backup-restore.yaml, deployment/restore.sh]
verknuepft: []
---

# QA-006: Zuverlässigkeit: Datensicherung

**Qualitätsmerkmal (ISO 25010):** Zuverlässigkeit (Wiederherstellbarkeit)

## Szenario
Geht der Datenbestand verloren, lässt er sich aus einer externen Sicherung wiederherstellen. Ist: Sicherung alle 6 Stunden (02, 08, 14, 20 Uhr), ein Wiederherstellungsskript spielt die jüngste Sicherung nach ausdrücklicher Bestätigung ein und ersetzt dabei den gesamten Bestand.

## Messbar durch
Sicherungs- und Wiederherstellungsjob im Deployment. Zielwerte für maximalen Datenverlust (RPO), Wiederanlaufzeit (RTO) und einen regelmäßigen Wiederherstellungstest sind nicht festgelegt ([RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md)).
