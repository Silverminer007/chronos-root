#!/usr/bin/env bash
# Fails if the local Claude settings file or a LiteLLM key header is tracked by git.
# See kb/constraints/C2026-10-20-001-no-local-agent-settings-in-git.md
set -u

settings_file=".claude/settings.local.json"
secret_marker="x-litellm-api-key"
status=0

if [ -n "$(git ls-files -- "$settings_file")" ]; then
  echo "ERROR: $settings_file is tracked. Run: git rm --cached $settings_file" >&2
  status=1
fi

# Excluded: this check and its test name the marker; litellm-update-tags builds the
# header from an env variable (printf placeholder, no literal key).
hits="$(git grep -n -i -l -e "$secret_marker" -- . \
  ':(exclude)scripts/check-no-local-settings.sh' \
  ':(exclude)scripts/check-no-local-settings.test.sh' \
  ':(exclude).claude-plugin/bin/litellm-update-tags')"
if [ -n "$hits" ]; then
  echo "ERROR: '$secret_marker' found in tracked files (values are not printed):" >&2
  echo "$hits" >&2
  status=1
fi

exit "$status"
