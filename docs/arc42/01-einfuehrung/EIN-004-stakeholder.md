---
id: EIN-004
title: "Stakeholder"
status: entwurf
typ: einfuehrung
quellen:
  - docs/req42/02-stakeholder/README.md
  - .github/CODEOWNERS
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/UserRole.java
verknuepft: [SH-001, SH-002, SH-003, SH-004, SH-005, SH-006, SH-007]
---

# EIN-004: Stakeholder

Stakeholder sind in [req42](../../req42/02-stakeholder/README.md) geführt. Hier stehen nur die Erwartungen an die Architektur und der Weg zu den Kapiteln, die sie bedienen.

| Rolle (Code) | Erwartung an die Architektur | Kapitel |
|---|---|---|
| Product Owner (SH-001; Code-Owner von `docs/` laut `CODEOWNERS`) | Freigabe verbindlicher Dokumentation, nachvollziehbare Entscheidungen | [Kap. 9](../09-entscheidungen/README.md), [RA-001](../02-randbedingungen/RA-001-branch-und-pr-workflow.md) |
| Gruppenleitung (`RESPONSIBLE`, SH-002) | Alle Mutationen am Termin, Überblick über Zusagen | [KZ-002](../08-konzepte/KZ-002-autorisierung.md), [LZ-003](../06-laufzeit/LZ-003-termin-anlegen.md) |
| Teilnehmer und Helfer (`ATTENDANT`, `HELPER`, `GUEST`; SH-003, SH-004) | Einfache Zu-/Absage, steuerbare Benachrichtigungen | [LZ-004](../06-laufzeit/LZ-004-zu-absage.md), [BB-013](../05-bausteine/BB-013-push-benachrichtigungen.md) |
| Administrator (`ADMIN_API`, SH-005) | Betreuung von Benutzern, Terminen, Gruppen, Push | [BB-015](../05-bausteine/BB-015-admin-bereich.md) |
| Betrieb (SH-006) | Reproduzierbares Deployment, Monitoring, wiederherstellbare Backups | [Kap. 7](../07-verteilung/README.md) |
| Entwicklung und Agenten (SH-007) | Klare Schichten, grüne CI, verlässliche Dokumentation | [LS-010](../04-loesungsstrategie/LS-010-qualitaets-gates-in-ci.md), [LS-011](../04-loesungsstrategie/LS-011-agenten-orchestrierter-betrieb.md) |
