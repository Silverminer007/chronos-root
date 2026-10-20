---
title: C2026-10-20-002: LiteLLM key in git history is leaked and must be rotated
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Situation

Until ticket #142, `.claude/settings.local.json` was tracked on `main` and contained a LiteLLM API key (header `ANTHROPIC_CUSTOM_HEADERS`). The repository is public, so the key must be treated as **leaked**. Removing the file from the index does not remove it from history.

## Action required (owner, not part of #142)

**Rotate the LiteLLM key.** History is deliberately not rewritten (see C2026-10-20-001). The key value is not recorded anywhere in this repository, ticket or PR.

## Commits on `main` that contain the file

| Commit | Date | Change |
|---|---|---|
| bb35ed9 | 2026-10-13 | Fix #133 (#134) |
| a2f17f5 | 2026-10-14 | Resolve #95 (#132) |
| 43b4828 | 2026-10-20 | Resolve #121 (#135) |
| 6a9c134 | 2026-10-20 | Resolve #122 (#129) |

The file also exists in branch-only commits (for example Fix pipeline / Address review commits of PRs #132, #134, #135, #138, #139, #141, and the merge on `agent/issue-97`). Find all with `git log --all -- .claude/settings.local.json`.
