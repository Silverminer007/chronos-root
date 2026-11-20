---
id: VT-002
title: Kubernetes-Ressourcen
status: entwurf
typ: verteilung
quellen:
  - deployment/templates
  - deployment/values.yaml
  - deployment/values-prod.yaml
  - deployment/Chart.yaml
  - deployment/github-actions-rbac.yaml
verknuepft: [VT-001, BB-019, BB-012, LS-012]
---

# Kubernetes-Ressourcen

Helm-Chart `chronos` (`Chart.yaml`, Version 0.1.0). Zuordnung der Bausteine:

| Baustein | Ressourcen | Eckdaten |
|---|---|---|
| Frontend ([BB-001](../05-bausteine/BB-001-frontend.md)) | Deployment `chronos-frontend`, Service, optional HPA | Container-Port 3000, Service-Port 80; Requests 256Mi/250m, Limits 512Mi/500m (Produktion) |
| Backend ([BB-005](../05-bausteine/BB-005-java-backend.md)) | Deployment `chronos-backend`, Service (Ports `http` 80 → 8080 und `metrics` 9000), optional HPA, `ServiceMonitor`, `Role` und `RoleBinding` `chronos-leader-election` | Requests 256Mi/250m, Limits 512Mi/500m; Liveness `/q/health/live`, Readiness `/q/health/ready` auf Port 9000; Rechte auf `coordination.k8s.io/leases` für den ServiceAccount `default` |
| Datenbank ([BB-018](../05-bausteine/BB-018-datenbank.md)) | PVC `postgres-pvc` (10Gi, `hcloud-volumes`), Deployment und Service `postgres` (1 Replikat, Image `postgres:16`), optional ConfigMap `postgres-init` für `extraDatabases` | Requests 256Mi/100m, Limits 512Mi/500m |
| Zugang | Ingress `chronos-ingress` (nginx, TLS mit cert-manager `letsencrypt-prod`, Weiterleitung auf den Frontend-Service) | Backend und Datenbank sind nicht öffentlich |
| Sicherung, Wiederherstellung | CronJobs `postgres-backup` und `postgres-restore-trigger` | [VT-005](VT-005-backup-restore.md) |
| Geheimnisse | `BitwardenSecret` oder `Secret`, optional Pull-Secret | [VT-003](VT-003-secrets-provider.md) |
| Alarme | `PrometheusRule` | [VT-006](VT-006-monitoring.md) |

Die Rolle `chronos-leader-election` ist die Voraussetzung für [LS-012](../04-loesungsstrategie/LS-012-leader-election-fuer-zeitgesteuerte-aufgaben.md). `github-actions-rbac.yaml` (nicht Teil des Charts, einmalig per `kubectl apply`) legt ServiceAccount und ClusterRole `github-actions-deployer` für die Deploy-Pipeline an. Weitere Dateien: `values-existing.yaml` (Provider `existing`), `values-values.yaml` (Testwerte), `bw-values.yaml` (Werte für den Bitwarden-Operator), `test-providers.sh` (rendert das Chart für alle Provider).
