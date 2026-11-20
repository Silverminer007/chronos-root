#!/usr/bin/env bash
# Claude Code Stop-Hook: Wurde Code geändert, aber das Wiki nicht gepflegt, einmal an die Wiki-Pflege erinnern.
# Blockiert höchstens einmal je Stop (stop_hook_active verhindert Schleifen).
set -u
input="$(cat)"
[ "$(printf '%s' "$input" | jq -r '.stop_hook_active // false')" = "true" ] && exit 0
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root" || exit 0
changes="$(git status --porcelain 2>/dev/null | cut -c4- | grep -v -e '^wiki/' -e '^docs/' -e '^\.idea/' || true)"
[ -z "$changes" ] && exit 0
if git status --porcelain 2>/dev/null | cut -c4- | grep -q '^wiki/log.md$'; then exit 0; fi
jq -n '{decision:"block",reason:"Es gibt Änderungen außerhalb von wiki/ und docs/, aber wiki/log.md wurde nicht ergänzt. Prüfe nach Skill chronos-wiki (Ingest): Gibt es etwas Wiederverwendbares zu lernen? Wenn ja, Wiki-Seite + wiki/index.md + wiki/log.md pflegen; wenn nein, einen Log-Eintrag \"kein Wiki-Bedarf\" anhängen."}'
