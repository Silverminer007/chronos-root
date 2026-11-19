---
id: VT-002
title: Kubernetes-Ressourcen
status: entwurf
typ: verteilung
quellen:
  - deployment/templates
  - deployment/values-prod.yaml
verknuepft: [BB-009]
---

# Kubernetes-Ressourcen

- **Frontend:** Deployment (Port 3000, Service 80), HPA 1–10 (CPU/Speicher 80 %), Requests 256Mi/250m, Limits 512Mi/500m.
- **Java-Backend** und optional **Rust-Backend:** Deployment, Service, HPA, ServiceMonitor; Rust Port 8080, Requests 64Mi/50m, Limits 128Mi/500m, Probes `/q/health/live` und `/q/health/ready`.
- **PostgreSQL:** eigenes Deployment/Service, optional mehrere Datenbanken (`extraDatabases`).
- **Ingress:** nginx, TLS über cert-manager (`letsencrypt-prod`).
- **Backend-Role/RBAC:** `backend-role.yaml` (Lease-Zugriff für Leader-Election **(?)**), `github-actions-rbac.yaml` für den Deploy-Account.
