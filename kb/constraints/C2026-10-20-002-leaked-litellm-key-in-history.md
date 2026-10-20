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

The file also exists in the following branch-only commits (not on `main`, but reachable in the public repository while the branches exist). Listed from `git log --all -- .claude/settings.local.json`, state of 2026-10-20; commits that delete the file are omitted:

| Commit | Subject |
|---|---|
| 8ff8737, a174f53 | Fix pipeline of pull request #134 |
| 89db4dc | Address review on pull request #132 |
| 7ea9e0f | Address review on pull request #135 |
| 8123cec | Fix pipeline of pull request #135 |
| d0acc87 | Fix pipeline of pull request #129 |
| e1a6009 | Address review on pull request #138 |
| af7cb1a, a19bd76, 2e768fe | Address review / Fix pipeline of pull request #139 |
| 2539c3c, 91c1f8c, 65caa58 | Implement #140 / Address review on pull request #141 |
| 83c4aad | Implement #98 |
| 3b8313d | Implement #97 |
| 78267b9, 2a855cb | Merge of `origin/main` into a branch (including `agent/issue-97`) |
| cf7026b | Implement #99 |

Branches may add more later; re-run `git log --all -- .claude/settings.local.json` to get the current list.
