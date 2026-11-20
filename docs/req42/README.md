---
title: req42 – Anforderungen
status: entwurf
---

# req42

Anforderungen nach [req42](https://www.req42.de/) mit den 12 Artefakten. Jede Entität hat eine eigene Datei mit Frontmatter (`id`, `title`, `status`, `typ`, `quellen`, `verknuepft`); Dateiname `<ID>-<kebab-titel>.md`. Nur `status: freigegeben` gilt, alles andere ist Entwurf. Unsicheres ist mit **(?)** markiert; Festlegungen, die nur ein Mensch treffen kann, stehen gebündelt in [RA-013](12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md), alle anderen Dateien verweisen darauf. Je Ordner beschreibt `00-kapitelbeschreibung.md` (offizielle Vorlage, unverändert) den Zweck.

| Nr. | Verzeichnis | Inhalt | ID-Schema |
|---|---|---|---|
| 1 | [01-ziele/](01-ziele/README.md) | Ziele | `Z-nnn` |
| 2 | [02-stakeholder/](02-stakeholder/README.md) | Stakeholder und Rollen | `SH-nnn` |
| 3 | [03-scope/](03-scope/README.md) | Scope-Abgrenzung | `SC-nnn` |
| 4 | [04-product-backlog/](04-product-backlog/README.md) | Features ([features/](04-product-backlog/features/README.md)) und Anforderungen mit Akzeptanzkriterien ([anforderungen/](04-product-backlog/anforderungen/README.md)) | `F-nnn`, `FA-nnn` |
| 5 | [05-modelle/](05-modelle/README.md) | Modelle | `M-nnn` |
| 6 | [06-qualitaet/](06-qualitaet/README.md) | Qualitätsanforderungen | `QA-nnn` |
| 7 | [07-randbedingungen/](07-randbedingungen/README.md) | Randbedingungen | `RB-nnn` |
| 8 | [08-terminologie/](08-terminologie/README.md) | Verweis auf das Glossar in arc42 | – |
| 9 | [09-betriebsmittel-personal/](09-betriebsmittel-personal/README.md) | Betriebsmittel und Personal | `BM-nnn` |
| 10 | [10-teamstruktur/](10-teamstruktur/README.md) | Teamstruktur | `TS-nnn` |
| 11 | [11-roadmaps/](11-roadmaps/README.md) | Roadmap | `RM-nnn` |
| 12 | [12-risiken-annahmen/](12-risiken-annahmen/README.md) | Risiken und Annahmen | `RA-nnn` |

**Verknüpfung:** Anforderung → Feature → Ziel und Stakeholder; Ziel → Stakeholder. Es wird nur in diese Richtung verlinkt, die Gegenrichtung ergibt sich aus den Dateien.

**IDs sind stabil.** Einträge werden nie umnummeriert und IDs entfallener Einträge nie wiederverwendet; ersetzte Einträge erhalten `status: ersetzt` mit Verweis. Entfallene IDs stehen in den Ordner-READMEs. Tickets, Tests und das Wiki verweisen auf diese IDs.

**Abgrenzung:** Dokumentiert ist, was Chronos in Betrieb leistet. Technische Lösung, Konzepte und Entscheidungen stehen in [arc42](../arc42/README.md), das Glossar dort in Kapitel 12.
