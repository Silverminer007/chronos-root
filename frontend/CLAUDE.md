# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
npm run dev        # Start dev server on port 3000
npm run build      # Production build
npm run preview    # Preview production build
npm run generate   # Static site generation
npm run lint       # ESLint (flat config via @nuxt/eslint)
npm run lint:fix   # ESLint with auto-fix
npm run typecheck  # TypeScript checking via nuxi typecheck
npm run test:e2e   # Playwright E2E tests (requires running server)
npm run test:lhci  # Lighthouse CI (starts server automatically)
```

After `npm install`, run `npm run lint` only after `nuxt prepare` has run (it is called automatically by `postinstall`). The `eslint.config.mjs` imports from `.nuxt/eslint.config.mjs` which is generated at that point.

### E2E Tests

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

Use `waitUntil: 'domcontentloaded'` (not `networkidle`) when navigating in tests — the landing page calls `fetchUser()` during SSR which proxies to the absent backend and would cause `networkidle` to hang.

## Architecture

This is a **Nuxt 3 + Vue 3** frontend that proxies to a Quarkus backend via server-side API routes.

### Auth Flow

Authentication uses **Keycloak OIDC**. Tokens are stored in httpOnly cookies (`kc_access`, `kc_refresh`). All API calls from the client go to Nuxt's `/api/v2/*` catch-all handler (`server/api/v2/[...path].ts`), which injects the `Authorization` header and proxies to the Quarkus backend at `NUXT_QUARKUS_URL`. `server/middleware/refresh.global.ts` transparently refreshes expiring tokens before each request.

The client-side global middleware (`app/middleware/auth.global.ts`) fetches the user via the Pinia auth store on each navigation and redirects to `/onboarding` when needed.

### Data Flow

- **Reads**: `useFetch('/api/v2/...')` — reactive, SSR-compatible
- **Mutations**: `$fetch('/api/v2/...')` — imperative calls inside store actions
- All business logic lives in Pinia stores under `app/stores/`

### Key Patterns

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

See `.env.example`. Key vars:
- `NUXT_AUTH_ISSUER`, `NUXT_AUTH_CLIENT_ID`, `NUXT_AUTH_CLIENT_SECRET`, `NUXT_AUTH_REDIRECT_URI` — Keycloak OIDC
- `NUXT_QUARKUS_URL` — backend base URL
- `NUXT_PUBLIC_SENTRY_DSN` — Sentry (optional)

### Deployment

Push to `master` → semantic versioned Docker image tagged `latest`. Push to `develop` → image tagged `develop`.

The CI pipeline (`.github/workflows/ci.yml`) runs on every push: secret detection (gitleaks), `npm audit`, ESLint + typecheck, Nuxt build, Playwright E2E, and Lighthouse CI. Docker build (`.github/workflows/docker-build.yml`) only triggers via `workflow_run` after CI passes — it will not run on a failed pipeline.
