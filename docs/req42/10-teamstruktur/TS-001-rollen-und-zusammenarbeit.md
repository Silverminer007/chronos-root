---
id: TS-001
title: Rollen und Zusammenarbeit
status: entwurf
typ: teamstruktur
quellen: [.github/CODEOWNERS, .agent-orchestrator/project.env, .agent-orchestrator/prompts/, AGENTS.md, docs/README.md, wiki/README.md]
verknuepft: [SH-001, SH-007]
---

# TS-001: Rollen und Zusammenarbeit

Das Repository zeigt ein Team mit einem Menschen und mehreren KI-Agenten; Personennamen und Zuständigkeiten darüber hinaus sind nicht belegt.

| Rolle | Besetzung | Zuständigkeit |
|---|---|---|
| Product Owner und Code-Owner | `@Silverminer007` | Priorität, Entscheidungen (`needs-po`), Freigabe und Merge von `docs/` und `.github/CODEOWNERS` |
| Entwicklung | `@Silverminer007` (Commits unter zwei Namen) | Umsetzung, Review, Merge |
| Agenten (Implementierung, Review, Auflösung, Konflikte) | Agent Orchestrator | arbeiten Tickets mit Label `ready-for-agent` ab; Bereiche `area:java`, `area:frontend`, `area:deployment`, `area:docs` |
| Steward | Agent | pflegt die Ticketschlange, stellt offene Fragen (`needs-input`, `needs-po`, `blocked`, `follow-up`) |
| Wiki-Pflege | Agenten (Claude Code, opencode) | `wiki/` lesen vor, pflegen nach jeder Aufgabe; Vorschläge für `docs/` nach `wiki/proposals/` |

**Ablauf:** Ticket (Label `ready-for-agent`) → Agent erstellt Branch und Pull Request (`Resolve #<Ticket>: <Titel>`, Body beginnt mit `Closes #<Ticket>`) → Review → Mensch merged ([RB-005](../07-randbedingungen/RB-005-aenderungen-an-main-nur-per-pull-request.md)). Agenten ändern `docs/` nie selbst. Arbeitseinheit der Agenten ist der **Task** (ein Ticket, ein PR); Epics, Features und Stories gliedern ihn fachlich ([Ticket-Typen](../04-product-backlog/ticket-typen.md)).

Weitere Teammitglieder, Teilteams und Vertretungsregeln sind nicht belegt ([RA-010](../12-risiken-annahmen/RA-010-betrieb-und-entwicklung-haengen-an-einer-person.md), [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md)).
