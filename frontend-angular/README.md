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
npm run test:lhci # Lighthouse CI (vorher build; BFF-Variablen und CHROME_PATH setzen, siehe .github/workflows/frontend-angular.yml)
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
    ├── _variables.scss    Figma-Foundations: Bootstrap-Variablen, Farb-Tokens, Verläufe, Schatten, Radien, Abstände; vor Bootstrap eingebunden
    └── _theme.scss        Custom Properties (`--chronos-*`), Typografie-Klassen, Gradient-Klassen (`text-gradient`, `bg-gradient-brand`, `btn-gradient`)
```

Container-Image (`Dockerfile`), E2E, Lighthouse, Parität und Cutover: `kb/adr/ADR-2026-10-21-014-angular-image-e2e-cutover.md`,
`kb/architecture/ARC42-angular-parity.md`, `kb/architecture/ARC42-angular-cutover.md`.

Layouts und öffentliche Seiten: `kb/adr/ADR-2026-10-21-006-angular-layouts-public-pages.md`. E2E-Tests liegen in `e2e/`.
`core/` im Detail: `kb/adr/ADR-2026-10-21-004-angular-core.md`. Toast-Container und Bestätigungsdialog sind in `App` eingebunden.
Umfrage und Admin-Bereich: `kb/adr/ADR-2026-10-21-013-angular-survey-admin.md` (kein Rollen-Guard, Absicherung nur im Backend).

## Theming und Dark Mode

Bootstrap-Variablen werden in `_variables.scss` überschrieben und über `@use` vor Bootstrap geladen (`src/styles.scss`).
Der `ThemeService` setzt `data-bs-theme` (`light`/`dark`) auf `<html>` nach `prefers-color-scheme` und folgt Änderungen.

### Design-Grundlagen (Figma „Foundations“)

- **Schrift:** Inter 400/600/700, selbst gehostet über `@fontsource/inter` (nur `latin`-Schnitte, in `angular.json` unter `styles`);
  `$font-family-sans-serif` fällt auf `system-ui` zurück.
- **Typografie:** Display 48/55 Bold (`.text-display`), H1 30/38 Bold, H2 24/32 Bold, H3 18/27 SemiBold, Body-M 16/24 (`.text-body-m`),
  Body-S 14/20 (`.text-body-s`), Caption 12/16 (`.text-caption`); SemiBold über `.fw-semibold`. `.modal-title` und `.card-heading` sind 18/27 SemiBold.
- **Farben:** Die Map `$chronos-colors` in `_variables.scss` enthält je Token Light und Dark. `_theme.scss` gibt sie als
  `--chronos-<token>` aus (z. B. `--chronos-bg-surface`, `--chronos-border-control`, `--chronos-text-muted`); `[data-bs-theme='dark']`
  überschreibt sie. Bootstrap-Variablen (`$body-bg`, `$success`, `*-dark`, ...) sind auf dieselben Werte gesetzt. Neue Farben zuerst dort ergänzen.
- **Verläufe:** `$brand-gradient` (Lila → Pink-700, `to right`), `$landing-gradient` (156,4°, ohne Dark-Variante), Overlay `$overlay-tint` (`rgba(0,0,0,.15)`);
  auch als `--chronos-gradient-brand`, `--chronos-gradient-landing`, `--chronos-overlay-tint`.
- **Schatten:** `$box-shadow-sm/-/-lg` (Figma sm/md/lg), `.card` nutzt sm; `--chronos-shadow-sm|md|lg`.
- **Radien:** `$border-radius`, `$btn-border-radius`, `$input-border-radius` 8 px; `$card-border-radius`, `$modal-content-border-radius`, `.glass-card` 12 px.
- **Abstände:** `$spacers` behält die Bootstrap-Stufen 0–5 (0/4/8/16/24/48 px) und ergänzt 6 = 12 px, 7 = 20 px, 8 = 32 px, 9 = 40 px,
  10 = 64 px, 11 = 80 px (z. B. `gap-6`, `p-7`).

## Bundle-Budgets

In `angular.json` (Produktion): initiales Bundle Warnung 600 kB / Fehler 750 kB roh (aktuell ca. 87 kB übertragen, Ziel Q-05: < 300 kB gzip);
Komponenten-Styles Warnung 2 kB / Fehler 4 kB.

## CI

`.github/workflows/frontend-angular.yml` führt bei Änderungen in diesem Verzeichnis `lint`, `test` und `build` aus
(Branches `main`, `develop`, `angular-frontend`).
