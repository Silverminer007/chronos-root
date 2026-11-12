---
title: Architekturübersicht
component: System
relates_to:
  - ADR-2025-09-28-001: Nuxt 3 + Vue 3
  - ADR-2026-10-12-014: Rust-Schema identisch zum Java-Schema
---

# Architekturübersicht

Chronos ist eine Gruppen- und Terminplanungs-App für Jugendorganisationen. Die Oberfläche ist deutsch.

## Komponenten

| Komponente | Verzeichnis | Rolle |
|---|---|---|
| Frontend (Nuxt) | `frontend/` | Nuxt 4 + Vue 3, SPA (`ssr: false`), PWA mit Service Worker, Pinia-Stores |
| Backend (Java) | `backend/` | Quarkus 3 / Java 21, REST-API, Referenzimplementierung |
| Rust-Backend | `rust-backend/` | Neuimplementierung der API; Deployment ist opt-in |
| Keycloak | extern | OIDC (Authorization Code Flow), Benutzerkonten |
| PostgreSQL | extern | Persistenz; Schema per Flyway (Java) bzw. SQL-Migrationen (Rust) |

Weitere Verzeichnisse: `deployment/` (Helm-Chart), `contract-tests/` (Paritätsprüfung Java/Rust), `kb/` (Wissensbasis).

## Datenfluss

```
Browser (SPA + Service Worker)
   │  /api/v2/...  (Cookies kc_access, kc_refresh, kc_expires)
   ▼
Nuxt-Server-Routen (server/api/v2/[...path].ts)
   │  Authorization: Bearer <kc_access>
   ▼
Backend (Quarkus bzw. Rust) ──► PostgreSQL
   │
   └─► Keycloak (Token-Validierung, Benutzersuche)
```

- Der Client ruft das Backend nie direkt auf; alle Aufrufe laufen über die Nuxt-Server-Routen
  ([ADR-2025-09-28-010](../adr/ADR-2025-09-28-010-server-routes-proxy.md)).
- Login und Token-Erneuerung laufen über `server/api/auth/*` und den Service Worker
  ([ADR-2025-09-28-003](../adr/ADR-2025-09-28-003-keycloak.md),
  [ADR-2025-09-28-005](../adr/ADR-2025-09-28-005-service-worker.md)).
- Das Backend ist geschichtet (Resource, Service, Domain, Repository) und löst Seiteneffekte wie
  Push-Benachrichtigungen über Events aus
  ([ADR-2025-09-28-007](../adr/ADR-2025-09-28-007-layered-backend.md)).

## Rust-Backend

Das Rust-Backend soll das Java-Backend ersetzen können. Der Deploy-Dispatch ist nur aktiv, wenn die
Repository-Variable `RUST_BACKEND_DEPLOY` auf `true` steht
([ADR-2026-10-20-018](../adr/ADR-2026-10-20-018-rust-deploy-opt-in.md)). Die Paritätsprüfung beschreibt
[ADR-2026-10-20-021](../adr/ADR-2026-10-20-021-contract-parity-harness.md).

## Schema-Identität

Das Rust-Schema ist identisch zum Java-Schema (Endstand der Flyway-Migrationen `V1.0.0 … V2.1.0`),
damit IDs und Verträge gleich bleiben. Jedes Backend besitzt seine eigene Datenbank
([ADR-2026-10-12-014](../adr/ADR-2026-10-12-014-rust-schema-identical-to-java.md)). Das Java-Schema
verwaltet Flyway ([ADR-2025-09-28-008](../adr/ADR-2025-09-28-008-postgres-flyway.md)).

## Weiterführend

- [Frontend-Technologie](../adr/ADR-2025-09-28-001-nuxt-vue.md)
- [Backend-Technologie](../adr/ADR-2025-09-28-002-quarkus.md)
- [README der Wissensbasis](../README.md)
