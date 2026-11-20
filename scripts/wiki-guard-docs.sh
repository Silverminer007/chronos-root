#!/usr/bin/env bash
# Claude Code PreToolUse-Hook (Write|Edit|NotebookEdit): Änderungen unter docs/ brauchen eine menschliche Freigabe.
# Liest den Hook-JSON von stdin und fragt den Menschen, statt still zu erlauben.
set -u
input="$(cat)"
path="$(printf '%s' "$input" | jq -r '.tool_input.file_path // .tool_input.notebook_path // empty')"
[ -z "$path" ] && exit 0
root="$(cd "$(dirname "$0")/.." && pwd)"
rel="${path#"$root"/}"
case "$rel" in
  docs/*)
    jq -n '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"ask",
      permissionDecisionReason:"docs/ ist die gemeinsam gepflegte, freigabepflichtige Wissensbasis. Ändern nur mit menschlicher Freigabe; sonst Vorschlag nach wiki/proposals/ (siehe docs/README.md)."}}'
    ;;
esac
exit 0
