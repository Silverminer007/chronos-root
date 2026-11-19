---
id: BB-009
title: Deployment: Helm-Chart (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - deployment/
  - DEPLOYMENT.md
verknuepft: [LS-009, VT-001, VT-002, VT-003]
---

# Deployment: Helm-Chart (Ebene 1)

Chart mit Templates für Frontend, Backend, Rust-Backend (Deployment, Service, HPA, ServiceMonitor), PostgreSQL, Backup/Restore, Ingress, Secrets, Prometheus-Alerts und Grafana-Dashboard. Werte: `values.yaml` plus `values-prod|staging|local|parity.yaml`. Testskripte: `test-providers.sh`, `test-parity-render.sh`. Hilfsdateien: `github-actions-rbac.yaml`, `restore.sh`, `parity/`.
