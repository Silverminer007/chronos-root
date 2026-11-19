---
id: RA-010
title: "Kubernetes/Helm-Deployment mit zwei Umgebungen"
status: entwurf
typ: randbedingung
quellen:
  - CLAUDE.md
  - deployment/README.md
verknuepft: []
---

# RA-010: Kubernetes/Helm-Deployment mit zwei Umgebungen

Produktion (`values-prod.yaml`, Image-Tag `latest`, Push auf `main`) und Staging (`values-staging.yaml`, Tag `develop`, Push auf `develop`). Backend-CI baut und pusht das Image und löst per `repository_dispatch` das Deployment aus; Frontend analog per `workflow_run`. Der Rust-Deploy ist optional (Repository-Variable `RUST_BACKEND_DEPLOY`).
