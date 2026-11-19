---
id: VT-004
title: CI/CD-Kette
status: entwurf
typ: verteilung
quellen:
  - .github/workflows
  - CLAUDE.md
verknuepft: [LS-010, LS-009]
---

# CI/CD-Kette

```mermaid
flowchart LR
  PR[Pull Request] --> CI[backend-ci / backend-rust-test / frontend-ci / contract-tests / helm-test / repo-hygiene]
  M[Push main/develop] --> B[Build + Push Image]
  B -->|repository_dispatch / workflow_run| D[deployment-ci: helm upgrade]
```
| Workflow | Inhalt |
|---|---|
| `backend-ci.yml` | Tests, `verify` (Checkstyle/SpotBugs/PMD), Versionsberechnung, Image |
| `backend-rust-test.yml` | fmt, clippy, Unit-/Integrationstests mit Postgres, Image (Deploy opt-in) |
| `frontend-ci.yml` | Secret-Detection, `npm audit`, Lint, Typecheck, Build |
| `contract-tests.yml` | Harness-Unit-Tests, `parity --coverage` |
| `deployment-helm-test.yml` | Provider- und Parity-Render-Tests |
| `deployment-ci.yml` | `helm upgrade --install` |
| `repo-hygiene.yml` | keine lokalen Agent-Einstellungen im Repo |
