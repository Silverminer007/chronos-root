---
title: arc42 – Architektur
status: entwurf
---

# arc42

Gliederung nach [arc42](https://arc42.org/). Kapitel werden nur angelegt, wenn sie etwas Verbindliches zu sagen haben.

| Kapitel | Datei | Herkunft im bisherigen `kb/` |
|---|---|---|
| 1 Einführung und Ziele | verweist auf `docs/vision` und `docs/req42` | – |
| 2 Randbedingungen | verweist auf `req42/04-randbedingungen.md` | `kb/constraints/` |
| 3 Kontext und Abgrenzung | `03-kontext.md` | Abschnitt „Core Mental Model“ in `LLM_WIKI.md` |
| 4 Lösungsstrategie | `04-loesungsstrategie.md` | ADR-2025-09-28-001…010 |
| 5 Bausteinsicht | `05-bausteine.md` | `kb/architecture/` |
| 6 Laufzeitsicht | `06-laufzeit.md` | Auth-Flow, Push-Pipeline |
| 7 Verteilungssicht | `07-verteilung.md` | `deployment/`, `DEPLOYMENT.md` |
| 8 Querschnittliche Konzepte | `08-konzepte.md` | `kb/patterns/` (nur bewährte, freigegebene) |
| 9 Architekturentscheidungen | `09-entscheidungen.md` | `kb/adr/` |
| 10 Qualität | verweist auf `req42/03` | – |
| 11 Risiken und technische Schulden | `11-risiken.md` | z. B. C2026-10-20-002 |
| 12 Glossar | verweist auf `wiki/concepts/glossar.md` oder `docs/arc42/12-glossar.md` | `kb/glossary/` |

Dieser Vorschlag legt nur Kapitel 3 und 9 als Beispiele an; der Rest folgt in Migrations-PRs (siehe `wiki/proposals/2026-11-19-migration-kb.md`).
