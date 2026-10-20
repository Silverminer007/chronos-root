#!/usr/bin/env bash
# Tests for check-no-local-settings.sh: runs it in throwaway git repos.
set -u
script="$(cd "$(dirname "$0")" && pwd)/check-no-local-settings.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
failures=0

run_case() { # name expected-exit setup-function
  local name="$1" expected="$2" setup="$3" repo="$tmp/$1"
  mkdir -p "$repo" && git -C "$repo" init -q
  (cd "$repo" && "$setup" && git add -A && "$script" >/dev/null 2>&1)
  local actual=$?
  if [ "$actual" -ne "$expected" ]; then
    echo "FAIL $name: expected exit $expected, got $actual"
    failures=$((failures + 1))
  else
    echo "ok   $name"
  fi
}

clean() { echo "hello" > README.md; }
tracked_file() { mkdir .claude && echo '{}' > .claude/settings.local.json; }
tracked_header() { printf 'x-litellm-api-key: placeholder\n' > notes.txt; }
ignored_file() { clean; mkdir .claude && echo '{}' > .claude/settings.local.json; echo ".claude/settings.local.json" > .gitignore; }

run_case clean-repo 0 clean
run_case ignored-settings-file 0 ignored_file
run_case tracked-settings-file 1 tracked_file
run_case tracked-litellm-header 1 tracked_header

exit "$failures"
