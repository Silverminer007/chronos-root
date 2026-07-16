# Load developer-local env vars (copy .env.dev.example → .env.dev and fill in values)
ifneq (,$(wildcard ./.env.dev))
  include .env.dev
  export
endif

.PHONY: dev infra backend frontend stop logs setup

## Start the full dev environment (infrastructure + backend + frontend).
## Requires tmux; otherwise prints instructions for separate terminals.
## Run `make setup` once after checkout before using this target.
dev: infra
	@echo "Infrastructure is up."
	@if command -v tmux >/dev/null 2>&1; then \
		tmux has-session -t chronos-dev 2>/dev/null || tmux new-session -d -s chronos-dev; \
		tmux new-window -t chronos-dev: -n backend  "make backend;  read"; \
		tmux new-window -t chronos-dev: -n frontend "make frontend; read"; \
		tmux select-window -t chronos-dev:backend; \
		tmux attach-session -t chronos-dev; \
	else \
		echo ""; \
		echo "Run each of the following in a separate terminal:"; \
		echo "  make backend   — Quarkus dev mode  (:8080, live reload)"; \
		echo "  make frontend  — Nuxt dev server   (:3000, HMR)"; \
	fi

## Start only the infrastructure services (PostgreSQL).
infra:
	docker compose -f backend/docker-compose.yaml up -d

## Start the Quarkus backend in dev mode (live reload).
backend: infra
	cd backend && ./mvnw quarkus:dev

## Start the Nuxt frontend dev server (HMR).
frontend: setup
	cd frontend && npm run dev

## Stop all infrastructure containers.
stop:
	docker compose -f backend/docker-compose.yaml down

## Tail infrastructure logs.
logs:
	docker compose -f backend/docker-compose.yaml logs -f

## Install frontend dependencies (run once after checkout).
setup:
	cd frontend && npm install
