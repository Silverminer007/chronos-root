# frontend-angular

Angular-Frontend von Chronos (Ersatz für `../frontend`, Nuxt). Anforderungen: `kb/requirements/REQ42-angular-frontend.md`,
Architektur: `kb/architecture/ARC42-angular-frontend.md`. Die Oberfläche ist komplett Deutsch.

**Stack:** Angular (Standalone, Signals, strict TypeScript), SCSS, Bootstrap 5 + Bootstrap Icons, Vitest (über `ng test`), angular-eslint.

## Befehle

```bash
npm ci
npm start        # BFF (Node) auf :3000, liefert dist/browser aus (vorher npm run build)
npm run start:dev # Angular-Dev-Server auf :4200
npm run lint     # angular-eslint
npm test         # Unit-Tests (Vitest, einmalig) und BFF-Tests (npm run test:server)
npm run build    # Produktions-Build inkl. Bundle-Budgets nach dist/
npm run test:e2e # Playwright gegen den BFF mit dist/browser (vorher build; einmalig `npx playwright install chromium`)
```

## BFF (`server/`)

Node/Express-Server: Auth-Routen (`/api/auth/*`), Proxy `/api/v2/*` mit Bearer-Token aus `kc_access`, `GET /api/config`
(Impressum), statisches Hosting mit SPA-Fallback und 301-Redirect nicht angemeldeter Anfragen auf `/`.
Konfiguration über `CHRONOS_AUTH_ISSUER`, `CHRONOS_AUTH_CLIENT_ID`, `CHRONOS_AUTH_REDIRECT_URI`, `CHRONOS_QUARKUS_URL`,
`CHRONOS_PUBLIC_IMPRESSUM_*` (Fallback auf `NUXT_*`), optional `CHRONOS_PORT`, `CHRONOS_STATIC_DIR`, `CHRONOS_COOKIE_SECURE`.
Details: `kb/adr/ADR-2026-10-21-003-angular-bff-server.md`.

## Aufbau

```
src/
├── app/
│   ├── layout/            DefaultLayout, LandingLayout (Router-Outlet-Rahmen), LandingFooter
│   ├── shared/            wiederverwendbare Komponenten (MessageDialog)
│   ├── core/              Modelle, AuthService, authGuard, errorInterceptor, ToastService, ModalService, ThemeService
│   ├── features/<name>/   je Feature eine lazy geladene Komponente
│   └── app.routes.ts      Routing (loadComponent je Route, Wildcard → Startseite)
└── styles/
    ├── _variables.scss    Bootstrap-Variablen und Markenfarben (Lila/Pink), vor Bootstrap eingebunden
    └── _theme.scss        Gradient-Klassen (`text-gradient`, `bg-gradient-brand`, `btn-gradient`)
```

Layouts und öffentliche Seiten: `kb/adr/ADR-2026-10-21-006-angular-layouts-public-pages.md`. E2E-Tests liegen in `e2e/`.
`core/` im Detail: `kb/adr/ADR-2026-10-21-004-angular-core.md`. Toast-Container und Bestätigungsdialog sind in `App` eingebunden.

## Theming und Dark Mode

Bootstrap-Variablen werden in `_variables.scss` überschrieben und über `@use` vor Bootstrap geladen (`src/styles.scss`).
Der `ThemeService` setzt `data-bs-theme` (`light`/`dark`) auf `<html>` nach `prefers-color-scheme` und folgt Änderungen.

## Bundle-Budgets

In `angular.json` (Produktion): initiales Bundle Warnung 600 kB / Fehler 750 kB roh (aktuell ca. 87 kB übertragen, Ziel Q-05: < 300 kB gzip);
Komponenten-Styles Warnung 2 kB / Fehler 4 kB.

## CI

`.github/workflows/frontend-angular.yml` führt bei Änderungen in diesem Verzeichnis `lint`, `test` und `build` aus
(Branches `main`, `develop`, `angular-frontend`).
