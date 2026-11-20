---
id: VT-007
title: Lokale Entwicklungsumgebung
status: entwurf
typ: verteilung
quellen:
  - Makefile
  - backend/docker-compose.yaml
  - .env.dev.example
  - frontend/.env.example
  - frontend/docker-compose.yaml
verknuepft: [KT-005, RA-013, BB-005, BB-001]
---

# Lokale Entwicklungsumgebung

| Baustein | Start | Port |
|---|---|---|
| PostgreSQL 18 (`chronos`/`chronos`) | `make infra` (`docker compose -f backend/docker-compose.yaml up -d`) | 5432 |
| Backend (Quarkus Dev Mode, Live-Reload) | `make backend` (`./mvnw quarkus:dev`) | 8080, Dev-UI `/q/dev/` |
| Frontend (Nuxt-Dev-Server, HMR) | `make frontend` (`npm run dev`) | 3000 |

`make setup` installiert die Frontend-Abhängigkeiten, `make dev` startet alles (in tmux-Fenstern, sonst Hinweis auf getrennte Terminals), `make stop` und `make logs` steuern die Infrastruktur. Das Makefile lädt `.env.dev` (Vorlage `.env.dev.example`: `AUTH_SERVER_*`, `VAPID_*`); das Frontend liest `frontend/.env` (Vorlage `.env.example`: `NUXT_AUTH_*`, `NUXT_QUARKUS_URL`). Keycloak läuft nicht lokal, sondern ist per `AUTH_SERVER_BASE_URL` zu konfigurieren. Im Profil `dev` hat das Backend Standardwerte für die Datenbank. `frontend/docker-compose.yaml` startet daneben (nur lokal) PostgreSQL 18, ein vorgebautes Backend-Image `chronos-api:test` (mit `LEADER_ELECTION_ENABLED=false`, Konfiguration aus `frontend/.env`) sowie Metabase (Port 5000) mit eigener PostgreSQL-Instanz (Port 5433); Dashboards oder Datenquellen sind nicht im Repository hinterlegt.
