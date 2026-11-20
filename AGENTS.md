# AGENTS.md

Gemeinsame Anweisungen für Coding-Agenten (opencode lädt diese Datei, Claude Code über `CLAUDE.md`).

## Wissensbasis (immer verwenden)

Chronos hat zwei Wissensbereiche:

| Bereich | Wer | Regel |
|---|---|---|
| `docs/` (Produktvision, req42, arc42) | Menschen und Agenten gemeinsam | **Nie ohne menschliche Freigabe ändern.** Siehe `docs/README.md`. |
| `wiki/` (LLM-Wiki) | Agenten | Lesen **vor**, pflegen **nach** jeder Aufgabe. Siehe `wiki/README.md`. |

Ablauf in jeder Session:

1. **Vor der Aufgabe:** `wiki/index.md` lesen und passende Seiten laden; für fachliche Fragen außerdem `docs/` (Anforderungen `docs/req42/`, Architektur `docs/arc42/`). Ist eine Wiki-Aussage von Code abhängig und `last-verified` alt, gegen den Code prüfen.
2. **Nach der Aufgabe:** Gelerntes im Wiki festhalten (Seite mit `sources`, `confidence`, `last-verified`), `wiki/index.md` ergänzen, Eintrag an `wiki/log.md` anhängen. Gibt es nichts zu lernen, Log-Eintrag „kein Wiki-Bedarf“.
3. **Widerspruch zu `docs/`** oder neue verbindliche Erkenntnis: Vorschlag nach `wiki/proposals/` (Vorlage `_vorlage.md`), `docs/` nicht ändern.
4. Keine Geheimnisse, Tokens oder personenbezogenen Daten ins Wiki.

Werkzeuge: Skill `chronos-wiki` (Claude Code und opencode), Befehle `/wiki-ingest`, `/wiki-lint`, `/wiki-query`.
Erzwingung: Claude Code über `.claude/settings.json` (Hooks), opencode über `opencode.json` und `.opencode/plugin/chronos-wiki.js`.

## Tickets

Ticket-Typen (Epic, Feature, Story, Task), Struktur und Definition of Done: `docs/req42/04-product-backlog/ticket-typen.md`. Neue Tickets mit den Vorlagen in `.github/ISSUE_TEMPLATE/` und Typ-Label (`epic`, `feature`, `story`, `task`) anlegen; `ready-for-agent` nur an vollständige Tasks vergeben.
