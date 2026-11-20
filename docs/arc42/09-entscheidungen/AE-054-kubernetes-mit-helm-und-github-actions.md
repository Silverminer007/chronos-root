---
id: AE-054
title: "Kubernetes mit Helm und GitHub Actions"
status: entwurf
typ: architekturentscheidung
datum: 2026-02-20
quellen:
  - deployment/Chart.yaml
  - deployment/values.yaml
  - deployment/values-prod.yaml
  - deployment/values-staging.yaml
  - deployment/templates/
  - .github/workflows/deployment-ci.yml
  - .github/workflows/backend-ci.yml
verknuepft: [AE-014, AE-052, RA-010, VT-001]
---

# AE-054: Kubernetes mit Helm und GitHub Actions

**Entscheidung:** Frontend, Backend und PostgreSQL laufen als ein Helm-Chart (`deployment/`, ein Release `chronos`) auf Kubernetes mit zwei Umgebungen: `chronos-staging` (`values-staging.yaml`, `staging.chronos-live.de`) und `chronos-prod` (`values-prod.yaml`, `chronos-live.de`). Ingress: nginx mit cert-manager (Let's Encrypt). Die Backend- und Frontend-CI bauen Images nach GHCR und lösen per `repository_dispatch` (`deploy-staging` bei `develop`, `deploy-production` bei `main`) den Workflow `deployment-ci.yml` aus, der mit `helm upgrade --install` ausrollt (Zugang über ServiceAccount, `github-actions-rbac.yaml`). PostgreSQL läuft im Cluster mit CronJob-Backup alle 6 h nach S3. Prometheus (ServiceMonitor, Alert-Regeln) überwacht Pods, Datenbank und Zertifikate. Frontend und Backend skalieren per HorizontalPodAutoscaler (Prod: Backend 1 bis 4, Frontend 1 bis 10 Replicas; Ziel 80 % CPU), die Datenbank nicht.

**Begründung:** nicht dokumentiert **(?)**; ersichtlich ist die Wiederverwendbarkeit des Charts für Staging, Prod, lokale Cluster und CI-Rendertests ([AE-014](AE-014-austauschbarer-secret-provider-im-helm-chart.md)).

**Alternativen:** nicht dokumentiert **(?)**.

**Konsequenzen:**
- Images nutzen den Tag `latest` (`pullPolicy: Always`); die Version überschreibt der Deploy-Workflow ("Override version").
- Die Datenbank liegt im Cluster statt als Managed Service ([R-006](../11-risiken/R-006-datenbank-ist-einziger-zustandsspeicher.md)).
- Das Backend braucht Zugriff auf die Kubernetes-API für die Leader-Election ([AE-052](AE-052-zeitgesteuerte-jobs-mit-leader-election.md)).
