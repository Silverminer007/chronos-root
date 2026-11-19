---
id: LS-009
title: Betrieb auf Kubernetes per Helm und GitHub Actions
status: entwurf
typ: strategie
quellen:
  - deployment/README.md
  - CLAUDE.md
verknuepft: [BB-009, VT-001, VT-004]
---

# Betrieb auf Kubernetes per Helm und GitHub Actions

Ein Helm-Chart für Frontend, Java-Backend, optionales Rust-Backend, PostgreSQL, Backup und Monitoring. Zwei Umgebungen (prod: `main`/`latest`, staging: `develop`). Secrets über austauschbare Provider (ADR-2026-10-03-001). Die CI baut Images und löst per `repository_dispatch` bzw. `workflow_run` das Deployment aus.
