# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

Chronos is a group scheduling and appointment management app for youth organisations. Users create appointments, invite friends and groups, track participation, and receive push notifications. The entire UI is in **German**.

## Development Workflow

- **Never commit directly to `main`.** Always create a new branch and open a draft PR for every change — no matter how small.
- Before making any change, consider its long-term sustainability: will this decision add complexity that's hard to reverse? Does it fit the existing architecture?
- When an assumption is unclear, stop and ask rather than guessing. It is better to clarify upfront than to undo a wrong implementation.

## Repository Layout

```
Chronos/
├── backend/      Quarkus 3 (Java 21) REST API
├── frontend/     Nuxt 3 + Vue 3 PWA (see frontend/CLAUDE.md for detail)
└── deployment/   Helm chart + GitHub Actions for Kubernetes
```

Each sub-project has its own CI pipeline triggered only by changes to its directory.

---

## Backend (`backend/`)

**Stack:** Quarkus 3, Java 21, Hibernate ORM with Panache, PostgreSQL, Flyway, Keycloak OIDC, MapStruct, Lombok, Micrometer/Prometheus.

### Commands

```bash
cd backend
./mvnw quarkus:dev            # dev mode with live reload at :8080; spins up DB via Docker
./mvnw test                   # unit tests (Testcontainers spins up PostgreSQL)
./mvnw -Dtest=AppointmentServiceTest test   # single test class
./mvnw verify                 # full build: tests + Checkstyle + SpotBugs + PMD
./mvnw package -DskipTests    # build only
```

Dev UI at `http://localhost:8080/q/dev/` — includes Swagger UI and DB console.

### Architecture

The backend follows a layered architecture within `de.chronos_live.chronos_date_api`:

| Package | Role |
|---|---|
| `presentation/` | JAX-RS resources — map HTTP in/out, delegate to services |
| `application/` | Business logic services; fire CDI events |
| `application/events/` | Immutable event records (Java `record`) used for decoupled side-effects (push notifications, reminders) |
| `application/reminder/` | Rule engine for scheduling appointment reminders; extensible via `ReminderRule` implementations |
| `domain/` | Hibernate/Panache entities — the source of truth for the data model |
| `infrastructure/` | Panache repositories; `WebPushAdapter` wraps the Web Push library |
| `mapper/` | MapStruct mappers — entity ↔ DTO conversions |
| `dto/` | Request/response shapes; `PagedResponse<T>` for paginated endpoints |
| `exception/` | Custom exception hierarchy + JAX-RS `ExceptionMapper` implementations |
| `security/` | `PrincipalContext` (request-scoped bean holding the resolved `User`) + `PrincipalContextFilter` |
| `config/` | CDI producers for `Clock` and push config |

A separate `de.chronos_live.admin` package mirrors the structure above for admin-only endpoints.

**Key cross-cutting patterns:**
- Services fire CDI events (`@Inject Event<...>`); `WebPushService` and `AppointmentReminderService` observe these to send push notifications asynchronously.
- Authorization is enforced in `AuthorizationService`, which checks role/participation membership — not in the JAX-RS layer.
- `PrincipalContextFilter` resolves the JWT → `User` entity once per request and stores it in the request-scoped `PrincipalContext`. All services inject `PrincipalContext` to get the current user.
- Database schema is managed by **Flyway** migrations in `src/main/resources/db/migration/` (versioned `V<major>.<minor>.<patch>__description.sql`). Tests use an additional `V99.0.0__test_fixtures.sql`.

### Code Quality Gates (`./mvnw verify`)

- **Checkstyle** — rules in `checkstyle.xml`; suppressions in `checkstyle-suppressions.xml`
- **SpotBugs** — exclusions in `spotbugs-exclude.xml`
- **PMD** — ruleset in `pmd-ruleset.xml`

All three run at the `verify` phase; CI enforces them on every PR.

### Environment Variables

Config is in `src/main/resources/application.properties`. Key variables (supplied via env or Docker):

| Variable | Purpose |
|---|---|
| `AUTH_SERVER_BASE_URL`, `AUTH_SERVER_REALM`, `AUTH_SERVER_CLIENT_ID`, `AUTH_SERVER_CLIENT_SECRET` | Keycloak OIDC |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection |
| `VAPID_MAILTO`, `VAPID_PUBLIC`, `VAPID_PRIVATE` | Web Push |

In `%dev` profile the DB defaults to `jdbc:postgresql://localhost:5432/chronos` with user/pass `chronos`.

---

## Frontend (`frontend/`)

See **`frontend/CLAUDE.md`** and **`frontend/code-style.md`** for the authoritative frontend reference. Key points:

- Nuxt 3 + Vue 3 (`<script setup lang="ts">` everywhere, never Options API)
- All API calls go through Nuxt server routes (`server/api/v2/[...path].ts`) which proxy to the Quarkus backend and inject auth headers — the client never calls the backend directly
- Authentication: Keycloak OIDC tokens stored in httpOnly cookies; transparent refresh in `server/middleware/refresh.global.ts`
- All user-facing text and error messages are in **German**

### Commands

```bash
cd frontend
npm install
npm run dev           # dev server at :3000
npm run lint          # ESLint
npm run lint:fix
npm run typecheck
npm run build
npm run test:e2e      # requires built app — see frontend/CLAUDE.md
```

---

## Deployment (`deployment/`)

Helm chart targeting Kubernetes. Two environments:

| Environment | Values file | Image tag | Triggered by |
|---|---|---|---|
| Production | `values-prod.yaml` | `latest` | push to `master` |
| Staging | `values-staging.yaml` | `develop` | push to `develop` |

CI pipeline: backend CI builds + pushes the Docker image, then fires a `repository_dispatch` event which triggers the deployment workflow. The frontend follows the same pattern via `workflow_run` chaining.

```bash
cd deployment
helm upgrade chronos . -f values-prod.yaml --namespace chronos-prod --install
helm upgrade chronos . -f values-staging.yaml --namespace chronos-staging --install
```