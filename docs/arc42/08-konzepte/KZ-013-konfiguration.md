---
id: KZ-013
title: Konfiguration
status: entwurf
typ: konzept
quellen:
  - backend/src/main/resources/application.properties
  - frontend/nuxt.config.ts
  - frontend/.env.example
  - .env.dev.example
  - deployment/values.yaml
verknuepft: [BB-001, BB-005, VT-003, VT-007, KT-003]
---

# Konfiguration

Alle umgebungsabhängigen Werte kommen aus Umgebungsvariablen; das Chart setzt sie aus `values*.yaml` (öffentliche Werte) und dem Secret `chronos-secret` ([VT-003](../07-verteilung/VT-003-secrets-provider.md)), lokal stehen sie in `.env.dev` bzw. `frontend/.env` ([VT-007](../07-verteilung/VT-007-lokale-entwicklungsumgebung.md)). Keine Geheimnisse im Repository ([RA-009](../02-randbedingungen/RA-009-geheimnisse-ueber-austauschbare-provider.md)).

| Variable | Komponente | Bedeutung |
|---|---|---|
| `AUTH_SERVER_BASE_URL`, `AUTH_SERVER_REALM` | Backend | Keycloak-URL und Realm für OIDC und Admin-Client (Standard `http://localhost:8080`, Realm `master`) |
| `AUTH_SERVER_CLIENT_ID`, `AUTH_SERVER_CLIENT_SECRET` | Backend | Client des Backends (Secret) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Backend | Datenbank; im Profil `dev` mit Standardwerten für `localhost` |
| `VAPID_MAILTO`, `VAPID_PUBLIC`, `VAPID_PRIVATE` | Backend | Web-Push-Schlüssel; ohne sie kein Push-Versand ([KT-004](../03-kontext/KT-004-push-dienste.md)) |
| `LEADER_ELECTION_ENABLED` | Backend | Standard `true`; `false` macht jede Instanz zum Leader ([LS-012](../04-loesungsstrategie/LS-012-leader-election-fuer-zeitgesteuerte-aufgaben.md)) |
| `QUARKUS_LOG_LEVEL` | Backend | Log-Level ([KZ-009](KZ-009-observability.md)) |
| `NUXT_AUTH_ISSUER`, `NUXT_AUTH_CLIENT_ID`, `NUXT_AUTH_REDIRECT_URI` | Frontend (Server) | Keycloak-Issuer, öffentlicher Client (ohne Secret) und Rücksprung-URL (`…/api/auth/callback`) |
| `NUXT_QUARKUS_URL` | Frontend (Server) | Backend-URL des Proxys (im Cluster `http://chronos-backend`) |
| `NUXT_PUBLIC_IMPRESSUM_*` | Frontend | Angaben für die Impressum-Seite |
| `NUXT_PUBLIC_SENTRY_DSN`, `SENTRY_AUTH_TOKEN` | Frontend | derzeit wirkungslos ([KT-008](../03-kontext/KT-008-sentry.md)) |

Quarkus-Profile: `dev` (`quarkus:dev`), `test` (Tests, [KZ-010](KZ-010-teststrategie.md)) und `prod` (Container; `DB_*` ohne Standardwerte). Das Frontend ist zur Laufzeit konfigurierbar: `NUXT_*` überschreibt `runtimeConfig` ohne neuen Build.
