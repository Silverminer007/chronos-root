---
title: arc42 – Architektur
status: entwurf
---

# arc42

Gliederung nach [arc42](https://arc42.org/). Jede Entität (Nachbarsystem, Baustein, Szenario, Entscheidung, Risiko, Begriff …) ist eine eigene Datei `<ID>-<titel>.md`; jedes Kapitelverzeichnis hat eine `README.md` als Index. Alle Einträge sind `status: entwurf`, Unsicheres ist mit **(?)** markiert.

| Kapitel | Verzeichnis | ID-Präfix |
|---|---|---|
| 1 Einführung und Ziele | [01-einfuehrung](01-einfuehrung/README.md) | EIN |
| 2 Randbedingungen | [02-randbedingungen](02-randbedingungen/README.md) (fachliche in [req42](../req42/07-randbedingungen/README.md)) | RA |
| 3 Kontext und Abgrenzung | [03-kontext](03-kontext/README.md) | KT |
| 4 Lösungsstrategie | [04-loesungsstrategie](04-loesungsstrategie/README.md) | LS |
| 5 Bausteinsicht | [05-bausteine](05-bausteine/README.md) | BB |
| 6 Laufzeitsicht | [06-laufzeit](06-laufzeit/README.md) | LZ |
| 7 Verteilungssicht | [07-verteilung](07-verteilung/README.md) | VT |
| 8 Querschnittliche Konzepte | [08-konzepte](08-konzepte/README.md) | KZ |
| 9 Architekturentscheidungen | [09-entscheidungen](09-entscheidungen/README.md) | AE |
| 10 Qualität | [10-qualitaet](10-qualitaet/README.md) | QS |
| 11 Risiken und technische Schulden | [11-risiken](11-risiken/README.md) | R, TD |
| 12 Glossar | [12-glossar](12-glossar/README.md) | G |

IDs sind stabil. Anforderungen stehen in [req42](../req42/README.md). Jedes Kapitelverzeichnis enthält `00-kapitelbeschreibung.md` (Hilfe der offiziellen arc42-Vorlage, Stand 2026-11-20). Dokumentiert sind Java/Quarkus-Backend und Nuxt-Frontend; das Rust-Experiment (`rust-backend/`) ist nicht in Betrieb und bewusst ausgeklammert.
