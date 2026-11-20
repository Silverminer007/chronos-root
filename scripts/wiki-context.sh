#!/usr/bin/env bash
# Gibt Agenten (Claude Code SessionStart-Hook, opencode-Plugin) die Wiki-Regeln und den Wiki-Index als Kontext.
# Aufruf aus beliebigem Verzeichnis; Ausgabe auf stdout.
set -u
root="$(cd "$(dirname "$0")/.." && pwd)"

cat <<'RULES'
## Chronos-Wissensbasis (verbindlich für diese Session)
- `docs/` (Produktvision, req42, arc42) ist von Menschen freigegeben: nie ohne ausdrückliche menschliche Freigabe ändern. Widersprüche oder neue verbindliche Erkenntnisse als Vorschlag nach `wiki/proposals/` (Vorlage `_vorlage.md`).
- `wiki/` ist das LLM-Wiki: Vor jeder Aufgabe unten den Index lesen und passende Seiten laden (Skill/Befehl `chronos-wiki`, Regeln in `wiki/README.md`). Hängt die Antwort vom Code ab und ist `last-verified` alt, gegen den Code prüfen.
- Nach der Aufgabe: Gelerntes ins Wiki schreiben (`sources`, `confidence`, `last-verified` setzen), `wiki/index.md` ergänzen, Eintrag in `wiki/log.md` anhängen. Keine Geheimnisse, Tokens oder personenbezogene Daten.
RULES

if [ -f "$root/wiki/index.md" ]; then
  printf '\n### wiki/index.md\n\n'
  cat "$root/wiki/index.md"
fi
if [ -f "$root/wiki/log.md" ]; then
  printf '\n### Letzte Einträge wiki/log.md\n\n'
  grep -n '^## \[' "$root/wiki/log.md" | tail -5 | cut -d: -f2-
fi
