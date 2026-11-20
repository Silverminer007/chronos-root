---
id: EIN-003
title: "Qualitätsziele"
status: entwurf
typ: einfuehrung
quellen:
  - docs/req42/06-qualitaet/README.md
  - deployment/templates/postgres-backup.yaml
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/LeaderElectionService.java
verknuepft: [RB-008, RB-001, QA-004, QA-006, QA-007, QA-008, QA-009]
---

# EIN-003: Qualitätsziele

Die höchstpriorisierten Qualitätsziele mit Szenarien und Messung stehen in [req42 (Qualität)](../../req42/06-qualitaet/README.md); Qualitätsszenarien der Architektur in [Kapitel 10](../10-qualitaet/README.md). Diese Tabelle zeigt, mit welchen Architekturmitteln die Ziele erreicht werden.

| Ziel | Anforderung | Architekturmittel |
|---|---|---|
| Sicherheit: Tokens für JavaScript unsichtbar, kein sichtbarer Token-Ablauf | QA-004, QA-009 | [LS-002](../04-loesungsstrategie/LS-002-server-routen-als-proxy.md), [LS-004](../04-loesungsstrategie/LS-004-service-worker-token-refresh.md), [KZ-007](../08-konzepte/KZ-007-sicherheit-und-tokens.md) |
| Bedienbarkeit: deutsche Oberfläche | RB-001 | [KZ-011](../08-konzepte/KZ-011-deutsche-oberflaeche.md) |
| Wartbarkeit: geschichtetes Backend, automatische Prüfungen in jedem PR | RB-008 | [LS-005](../04-loesungsstrategie/LS-005-geschichtetes-backend.md), [LS-010](../04-loesungsstrategie/LS-010-qualitaets-gates-in-ci.md) |
| Zuverlässigkeit: Erinnerungen genau einmal bei mehreren Backend-Instanzen | QA-008 | [LS-012](../04-loesungsstrategie/LS-012-leader-election-fuer-zeitgesteuerte-aufgaben.md) |
| Zuverlässigkeit: Datensicherung | QA-006 | [VT-005](../07-verteilung/VT-005-backup-restore.md) |
| Betreibbarkeit: Metriken, Health, Alarme | QA-007 | [KZ-009](../08-konzepte/KZ-009-observability.md), [VT-006](../07-verteilung/VT-006-monitoring.md) |
