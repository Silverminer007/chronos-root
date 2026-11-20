---
id: QS-index
title: "10 Qualität"
status: entwurf
typ: index
quellen:
  - .github/workflows/
  - backend/pom.xml
  - frontend/.lighthouserc.json
  - deployment/templates/prometheus-alerts.yaml
verknuepft: [RB-001, RB-008, RB-011, QA-004, QA-005, QA-006, QA-007, QA-008, QA-009, QA-010, QA-011]
---

# 10 Qualität

Die Qualitätsziele und ihre Priorität stehen in req42 (`QA-nnn`) und in [EIN-003](../01-einfuehrung/EIN-003-qualitaetsziele.md); hier steht, wie sie sich in Qualitätsmerkmale zerlegen und anhand konkreter Szenarien (`QS-nnn`: Reiz, Antwort, Messung) prüfbar sind. Jedes Szenario nennt seinen Nachweis (Test, CI-Job, Konfiguration) oder markiert die Lücke mit **(?)**. Die deutsche Oberfläche (RB-001) ist eine Randbedingung ohne eigenes Szenario; ihre Lücke steht in [TD-009](../11-risiken/TD-009-englische-fehlermeldungen-im-backend.md).

## Qualitätsbaum

```text
Qualität
├── Wartbarkeit
│   └── Qualitätsprüfungen vor dem Merge (RB-008) ........ QS-002
├── Benutzbarkeit
│   ├── Sitzung ohne sichtbaren Token-Ablauf (QA-004) .... QS-006
│   ├── Web-Qualität öffentlicher Seiten (QA-010) ........ QS-015
│   └── Smartphone und Installation (QA-011) ............. QS-016
├── Sicherheit
│   ├── Token für Skripte unlesbar (QA-009) .............. QS-017
│   ├── Zugriffsschutz Admin-API ......................... QS-007
│   ├── Termin-Berechtigung nach Rolle ................... QS-013
│   └── Keine Geheimnisse im Repository (RB-011) ......... QS-014
├── Zuverlässigkeit
│   ├── Genau ein Scheduler trotz Replicas (QA-008) ...... QS-010
│   ├── Veraltete Push-Abos bereinigen ................... QS-011
│   └── Datensicherung und Wiederherstellung (QA-006) .... QS-012
├── Leistungseffizienz
│   └── Antwortzeiten unter Last (QA-005) ................ QS-009
└── Betreibbarkeit
    └── Alarme bei Ausfall und Ressourcenmangel (QA-007) . QS-008
```

| ID | Szenario | Merkmal | Qualitätsziel |
|---|---|---|---|
| [QS-002](QS-002-qualitaetspruefungen-vor-dem-merge.md) | Qualitätsprüfungen vor dem Merge | Wartbarkeit | RB-008 |
| [QS-006](QS-006-token-ablauf-ohne-sichtbaren-fehler.md) | Token-Ablauf ohne sichtbaren Fehler | Benutzbarkeit | QA-004 |
| [QS-007](QS-007-zugriffsschutz-der-admin-api.md) | Zugriffsschutz der Admin-API | Sicherheit | QA-009 |
| [QS-008](QS-008-betriebsueberwachung.md) | Betriebsüberwachung | Betreibbarkeit | QA-007 |
| [QS-009](QS-009-lastverhalten.md) | Lastverhalten | Leistungseffizienz | QA-005 |
| [QS-010](QS-010-genau-ein-scheduler-trotz-replicas.md) | Genau ein Scheduler trotz Replicas | Zuverlässigkeit | QA-008 |
| [QS-011](QS-011-veraltete-push-abos-bereinigen.md) | Veraltete Push-Abos bereinigen | Zuverlässigkeit | kein req42-Ziel (Architekturszenario) |
| [QS-012](QS-012-datensicherung-und-wiederherstellung.md) | Datensicherung und Wiederherstellung | Zuverlässigkeit | QA-006 |
| [QS-013](QS-013-termin-berechtigung-nach-rolle.md) | Termin-Berechtigung nach Rolle | Sicherheit | QA-009 |
| [QS-014](QS-014-keine-geheimnisse-im-repository.md) | Keine Geheimnisse im Repository | Sicherheit | RB-011 |
| [QS-015](QS-015-web-qualitaet-der-oeffentlichen-seiten.md) | Web-Qualität der öffentlichen Seiten | Benutzbarkeit | QA-010 |
| [QS-016](QS-016-nutzbarkeit-auf-dem-smartphone.md) | Nutzbarkeit und Installierbarkeit auf dem Smartphone | Benutzbarkeit | QA-011 |
| [QS-017](QS-017-token-cookies-fuer-skripte-unlesbar.md) | Token-Cookies für Skripte unlesbar | Sicherheit | QA-009 |

IDs sind stabil. Entfallene IDs (nicht wiederverwenden): QS-001 und QS-003 (früher entfallen); QS-004 (Frontend-Prüfungen, aufgegangen in [QS-002](QS-002-qualitaetspruefungen-vor-dem-merge.md)); QS-005 (deutsche Oberfläche, Wiederholung von RB-001, Lücke jetzt in [TD-009](../11-risiken/TD-009-englische-fehlermeldungen-im-backend.md)). QS-002 hieß zuvor „Statische Qualität Java“.
