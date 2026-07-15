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
├── frontend/     Nuxt 3 + Vue 3 PWA
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
| `security/` | `PrincipalContext` (request-scoped bean holding the current user's OIDC ID) + `PrincipalContextFilter` |
| `config/` | CDI producers for `Clock` and push config |

A separate `de.chronos_live.admin` package mirrors the structure above for admin-only endpoints.

**Key cross-cutting patterns:**
- Services fire CDI events (`@Inject Event<...>`); `WebPushService` and `AppointmentReminderService` observe these to send push notifications asynchronously.
- Authorization is enforced in `AuthorizationService`, which checks role/participation membership — not in the JAX-RS layer.
- `PrincipalContextFilter` resolves the JWT once per request and stores the OIDC subject in the request-scoped `PrincipalContext`. All services inject `PrincipalContext` to get the current user's OIDC ID.
- Database schema is managed by **Flyway** migrations in `src/main/resources/db/migration/` (versioned `V<major>.<minor>.<patch>__description.sql`). Tests load additional fixtures from `src/test/resources/db/testdata/`.

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

**Stack:** Nuxt 4 + Vue 3 PWA, SPA mode (`ssr: false`). `<script setup lang="ts">` everywhere, never Options API. All API calls go through Nuxt server routes (`server/api/v2/[...path].ts`) which proxy to the Quarkus backend — the client never calls the backend directly. All user-facing text and error messages are in **German**. See **`frontend/code-style.md`** for code style conventions.

### Commands

```bash
cd frontend
npm install
npm run dev           # dev server at :3000
npm run build         # production build
npm run preview       # preview production build
npm run generate      # static site generation
npm run lint          # ESLint (flat config via @nuxt/eslint)
npm run lint:fix      # ESLint with auto-fix
npm run typecheck     # TypeScript checking via nuxi typecheck
npm run test:e2e      # Playwright E2E tests (requires running server)
npm run test:lhci     # Lighthouse CI (starts server automatically)
```

After `npm install`, run `npm run lint` only after `nuxt prepare` has run (called automatically by `postinstall`). The `eslint.config.mjs` imports from `.nuxt/eslint.config.mjs` which is generated at that point.

#### E2E Tests

Tests live in `e2e/` and use Playwright. Before running, build the app and start the preview server:

```bash
npm run build
node .output/server/index.mjs &   # start preview server
npm run test:e2e
```

The Quarkus backend does **not** need to be running. The server middleware redirects unauthenticated requests (no cookies) to `/`. Tests cover:
- `e2e/landing.spec.ts` — landing page content and nav links
- `e2e/public-pages.spec.ts` — `/public/*` pages + PWA manifest
- `e2e/auth-redirects.spec.ts` — verifies server-side 301 redirects for protected routes

Install browsers once with `npx playwright install chromium`.

Use `waitUntil: 'domcontentloaded'` (not `networkidle`) when navigating in tests.

### Architecture

This is a **Nuxt 4 + Vue 3** SPA that proxies to a Quarkus backend via server-side API routes.

#### Auth Flow

Authentication uses **Keycloak OIDC** (Authorization Code flow). Dedicated server routes under `server/api/auth/` handle the full lifecycle:

| Route | Role |
|---|---|
| `GET /api/auth/login` | Redirects to Keycloak's authorization endpoint |
| `GET /api/auth/callback` | Exchanges the authorization code for tokens; sets httpOnly cookies |
| `GET /api/auth/isLoggedIn` | Checks `kc_expires` cookie expiry — used by client middleware |
| `POST /api/auth/refresh` | Exchanges `kc_refresh` for new tokens and updates all three cookies |
| `POST /api/auth/logout` | Clears auth cookies |

Three cookies are set after login: `kc_access` (httpOnly, the access token), `kc_refresh` (httpOnly, the refresh token), and `kc_expires` (client-readable Unix timestamp of `kc_access` expiry).

On every navigation, the client middleware `app/middleware/auth.global.ts` calls `authStore.checkSession()` → `GET /api/auth/isLoggedIn`. If the session is invalid it redirects to `/`; if the profile is incomplete it redirects to `/onboarding`.

The `/api/v2/[...path].ts` catch-all reads `kc_access` from the cookie and forwards it as `Authorization: Bearer ...` to the Quarkus backend.

#### Service Worker (`public/push-sw.js`)

The service worker is the central token-refresh mechanism. It intercepts **all `/api/*` requests** via the `fetch` event and handles two cases:

- **`/api/auth/isLoggedIn`** — answered synthetically: the SW reads `kc_expires` directly from the `cookieStore` and returns 204 or 401 without a server round-trip. If the token expires in under 5 s it refreshes first.
- **All other `/api/v2/*` requests** — if the token expires in under 5 s, it proactively calls `POST /api/auth/refresh` before forwarding the request. If the server still returns 401 it retries once after refreshing.
- **`/api/auth/*` routes** — passed through to the server unchanged.

Beyond request interception, the SW also:
- **Schedules proactive refresh** via `setTimeout` 30 s before `kc_expires` (set on activate and re-set whenever `kc_expires` changes in `cookieStore`).
- **Notifies clients on session expiry** — if `kc_expires` is deleted from `cookieStore`, it posts `{ type: 'SESSION_EXPIRED' }` to all open tabs, which triggers a redirect to `/` via `app/plugins/auth.client.ts`.
- Deduplicates concurrent refresh calls with an `inflightRefresh` promise.

#### Data Flow

Since SSR is disabled this is a pure SPA — all data fetching happens client-side:

- **Reads**: `useFetch('/api/v2/...')` — reactive, component-bound
- **Mutations**: `$fetch('/api/v2/...')` — imperative calls inside store actions
- All business logic lives in Pinia stores under `app/stores/`

#### Key Patterns

- `app/types/index.ts` — all shared TypeScript interfaces and enums (`Role`, `ParticipationStatus`, `AppointmentStatus`, etc.)
- `app/layouts/default.vue` — authenticated shell; wraps every protected page
- `app/layouts/landingpage.vue` — public layout
- Pages under `app/pages/public/` use the landingpage layout; all others require auth
- `app/pages/mockup/` — development prototypes, not production pages

### UI Conventions

- **Language**: All UI text in German
- **Icons**: `<Icon name="lucide:..."/>` or `<Icon name="simple-icons:..."/>`
- **Toast**: `const toast = useToast()` from PrimeVue
- **Navigation**: `navigateTo('/path')`
- **Colors**: Purple-600/pink-500 gradient as primary brand; always pair dark mode classes (e.g. `bg-white dark:bg-neutral-800`)
- **PrimeVue theme**: Custom ChronosTheme in `theme.ts` (Aura preset, indigo primary)

### Environment Variables

See `frontend/.env.example`. Key vars:

| Variable | Purpose |
|---|---|
| `NUXT_AUTH_ISSUER` | Keycloak realm URL (e.g. `https://keycloak/realms/realm`) |
| `NUXT_AUTH_CLIENT_ID` | Keycloak client ID |
| `NUXT_AUTH_REDIRECT_URI` | OAuth2 callback URL (e.g. `https://app/api/auth/callback`) |
| `NUXT_QUARKUS_URL` | Backend base URL |
| `NUXT_PUBLIC_SENTRY_DSN` | Sentry DSN (optional) |
| `NUXT_PUBLIC_IMPRESSUM_NAME`, `…_STREET`, `…_CITY`, `…_EMAIL`, `…_PHONE` | Legal Impressum page content |

---

## Deployment (`deployment/`)

Helm chart targeting Kubernetes. Two environments:

| Environment | Values file | Image tag | Triggered by |
|---|---|---|---|
| Production | `values-prod.yaml` | `latest` | push to `main` |
| Staging | `values-staging.yaml` | `develop` | push to `develop` |

CI pipeline: backend CI builds + pushes the Docker image, then fires a `repository_dispatch` event which triggers the deployment workflow. The frontend follows the same pattern via `workflow_run` chaining.

```bash
cd deployment
helm upgrade chronos . -f values-prod.yaml --namespace chronos-prod --install
helm upgrade chronos . -f values-staging.yaml --namespace chronos-staging --install
```