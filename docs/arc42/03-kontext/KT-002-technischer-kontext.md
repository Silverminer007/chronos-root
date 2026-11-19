---
id: KT-002
title: Technischer Kontext
status: entwurf
typ: kontext
quellen:
  - frontend/server/api
  - deployment/templates
  - kb/adr/ADR-2025-09-28-010-server-routes-proxy.md
verknuepft: [KT-003, KT-004, KT-005, BB-001, BB-002]
---

# Technischer Kontext

```mermaid
flowchart LR
  B[Browser / PWA] -->|/api/*| N[Nuxt-Server-Routen]
  N -->|Bearer kc_access| J[Java-Backend Quarkus]
  N -.optional.-> R[Rust-Backend axum]
  J --> P[(PostgreSQL)]
  R --> P
  N <-->|OIDC| K[Keycloak]
  J & R -->|Admin-/Userinfo-API| K
  J & R -->|Web Push, VAPID| W[Push-Dienste der Browser]
  P -->|pg_dump, CronJob| S3[(S3-Backup)]
  J & R -->|/q/metrics| M[Prometheus / Grafana]
  F[Frontend] -->|Fehler| SE[Sentry]
```

- Der Browser spricht nie direkt mit dem Backend (RB-004); alle Aufrufe laufen über `/api/v2/[...path].ts`.
- Java- und Rust-Backend sind API-kompatibel (QA-001); der Betrieb des Rust-Backends ist optional (`kb/adr/ADR-2026-10-20-018-rust-deploy-opt-in.md`).
- Zugriffsschutz über OIDC (RB-002).
