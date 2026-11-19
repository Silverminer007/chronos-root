---
title: req42 – Anforderungen
status: entwurf
---

# req42

Anforderungen nach [req42](https://www.req42.de/), analog zu arc42 gegliedert. Jede Datei hat ein `status`-Feld im Frontmatter; nur `freigegeben` gilt.

| Datei | Inhalt |
|---|---|
| [01-ziele-und-stakeholder.md](01-ziele-und-stakeholder.md) | Ziele, Stakeholder, Personas |
| [02-anforderungen.md](02-anforderungen.md) | Funktionale Anforderungen (`FA-nnn`) |
| [03-qualitaetsanforderungen.md](03-qualitaetsanforderungen.md) | Qualitätsziele und -szenarien (`QA-nnn`) |
| [04-randbedingungen.md](04-randbedingungen.md) | Randbedingungen (`RB-nnn`), z. B. deutsche UI, OIDC |

**IDs sind stabil.** Eine Anforderung wird nie umnummeriert; sie wird `status: ersetzt` mit Verweis auf die Nachfolgerin.
Tickets, Tests und das Wiki verweisen auf diese IDs.
