---
title: C2026-10-20-001: Local agent settings must not be tracked in git
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Constraint

`.claude/settings.local.json` must never be tracked. The agent tooling writes it into every worktree and it holds a LiteLLM API key in `ANTHROPIC_CUSTOM_HEADERS`. The repository is public.

## Enforcement (ticket #142)

- `.claude/settings.local.json` is in `.gitignore` and was removed from the index (`git rm --cached`).
- Workflow `.github/workflows/repo-hygiene.yml` runs `scripts/check-no-local-settings.sh` on every push and pull request to `main`/`develop` (no path filter, so a PR that only adds the file is caught). The script fails if the file is tracked or if the LiteLLM key header name appears in any tracked file; it prints file names only, never values.
- `scripts/check-no-local-settings.test.sh` covers the check (clean, ignored, tracked file, tracked header) and runs before the check in CI.
- `.claude-plugin/bin/litellm-update-tags` is allowlisted: it builds the header from an environment variable and contains no literal key.

## Decisions

- **Own workflow instead of a step in the Rust CI:** `backend-rust-test.yml` is path-filtered to `rust-backend/**`, so a PR touching only `.claude/` would not trigger it.
- **No history rewrite:** rewriting public history would break every open branch and PR and does not undo exposure of a key that was already public.
