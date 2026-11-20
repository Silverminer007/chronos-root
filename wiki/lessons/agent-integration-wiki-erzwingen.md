---
title: Wiki-Nutzung in Claude Code und opencode erzwingen
type: lesson
sources:
  - AGENTS.md
  - .claude/settings.json
  - .opencode/plugin/chronos-wiki.js
  - scripts/wiki-context.sh
  - scripts/wiki-stop-check.sh
confidence: mittel
last-verified: 2026-11-20
---

# Wiki-Nutzung in Claude Code und opencode erzwingen

| Zweck | Claude Code | opencode |
|---|---|---|
| Regeln laden | `CLAUDE.md` mit `@AGENTS.md` | `opencode.json` → `instructions` |
| Index in jede Session | SessionStart-Hook → `scripts/wiki-context.sh` | Plugin-Hook `experimental.chat.system.transform` |
| `docs/` schützen | PreToolUse-Hook `wiki-guard-docs.sh` (fragt nach) | Plugin-Hook `tool.execute.before` (wirft Fehler) |
| Pflege erinnern | Stop-Hook `wiki-stop-check.sh` (blockiert einmal) | – (kein Gegenstück) |

## Stolperstellen

- Die `docs/`-Sperre erfasst nur Write/Edit, nicht Shell-Befehle (`sed`, `git mv`).
- `wiki-stop-check.sh` zählt jede Änderung außerhalb von `wiki/`, `docs/` und `.idea/`, also auch vorgemerkte Löschungen; das Log gilt als gepflegt, wenn `wiki/log.md` jünger ist als jede geänderte Datei (Änderungszeit, auch wenn schon committed). Sonst blockiert er einmal; `stop_hook_active` verhindert Schleifen.
- opencode kennt `.opencode/plugin/` und `.opencode/plugins/` (1.18.30); der Hook `experimental.chat.system.transform` ist experimentell und kann sich ändern.
- `CLAUDE.md` nie komplett ersetzen: Der Import `@AGENTS.md` wird **angehängt**, die Projektregeln bleiben (ein Commit hatte sie versehentlich entfernt, im Folgecommit korrigiert).
- Beim Start der Session waren 80 Dateien (`kb/`, `CLAUDE.md`, `README.md` …) als gelöscht vorgemerkt, nicht von diesem Agenten. Vor dem Commit nur eigene Pfade angeben (`git commit -- <pfade>`).
