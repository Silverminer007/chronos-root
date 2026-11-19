---
title: req42 – Anforderungen
status: entwurf
---

# req42

Anforderungen nach [req42](https://www.req42.de/). Jede Entität (Ziel, Stakeholder, Feature, Anforderung, Qualitätsanforderung, Randbedingung) hat eine eigene Datei mit Frontmatter (`id`, `title`, `status`, `typ`, `quellen`, `verknuepft`). Nur `status: freigegeben` gilt; alles andere ist Entwurf. Unsicheres ist mit **(?)** markiert.

| Verzeichnis | Inhalt | ID-Schema |
|---|---|---|
| [01-ziele/](01-ziele/README.md) | Ziele | `Z-nnn` |
| [02-stakeholder/](02-stakeholder/README.md) | Stakeholder und Rollen | `SH-nnn` |
| [03-features/](03-features/README.md) | Features (Funktionsumfang) | `F-nnn` |
| [04-anforderungen/](04-anforderungen/README.md) | Funktionale Anforderungen mit Akzeptanzkriterien | `FA-nnn` |
| [05-qualitaet/](05-qualitaet/README.md) | Qualitätsanforderungen | `QA-nnn` |
| [06-randbedingungen/](06-randbedingungen/README.md) | Randbedingungen | `RB-nnn` |

Dateiname: `<ID>-<kebab-titel>.md`. Verknüpfung: Anforderung → Feature → Ziel und Stakeholder.

**IDs sind stabil.** Eine Anforderung wird nie umnummeriert; sie wird `status: ersetzt` mit Verweis auf die Nachfolgerin. Tickets, Tests und das Wiki verweisen auf diese IDs.
