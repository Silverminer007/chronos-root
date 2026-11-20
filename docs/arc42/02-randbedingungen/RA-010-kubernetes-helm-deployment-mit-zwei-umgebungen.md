---
id: RA-010
title: "Kubernetes/Helm-Deployment mit zwei Umgebungen"
status: entwurf
typ: randbedingung
quellen:
  - deployment/Chart.yaml
  - deployment/values.yaml
  - deployment/templates/ingress.yaml
  - deployment/templates/backend-service-monitor.yaml
  - deployment/templates/prometheus-alerts.yaml
  - .github/workflows/deployment-ci.yml
verknuepft: [RB-010, VT-001, VT-002, KT-007]
---

# RA-010: Kubernetes/Helm-Deployment mit zwei Umgebungen

Betrieben wird ausschließlich als Helm-Chart (`chronos`) auf einem Kubernetes-Cluster; die Deploy-Pipeline spricht einen k3s-Cluster an (Cluster-Name `k3s-cluster` in `deployment-ci.yml`). Zwei Umgebungen in getrennten Namespaces (`chronos-prod`, `chronos-staging`), Zuordnung zu Branches nach RB-010 ([VT-001](../07-verteilung/VT-001-umgebungen.md)).

Der Cluster muss bereitstellen, was das Chart voraussetzt:

| Voraussetzung | Grund |
|---|---|
| Ingress-Controller `nginx` | `ingress.className: nginx` |
| cert-manager mit ClusterIssuer `letsencrypt-prod` | TLS-Zertifikat (`ingress.yaml`) |
| Prometheus Operator (`ServiceMonitor`, `PrometheusRule`, Label `release: prometheus`) | Metriken und Alarme ([KT-007](../03-kontext/KT-007-prometheus-grafana.md)) |
| Blackbox-Exporter-Metrik `probe_ssl_earliest_cert_expiry` | Alarm `SSLCertExpiringSoon` |
| Bitwarden Secrets Manager Operator | nur bei `secrets.provider: bitwarden` ([KT-012](../03-kontext/KT-012-bitwarden.md)) |
| StorageClass `hcloud-volumes` | Postgres-Volume (`postgresql.storage.storageClass`) |
| Image-Pull-Secret `chronos-docker-secret` | Images aus `ghcr.io` ([KT-011](../03-kontext/KT-011-github.md)) |
