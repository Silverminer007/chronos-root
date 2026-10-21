# ADR-2026-10-21-002: Projektgerüst `frontend-angular` (Ticket #176)

Status: umgesetzt (Branch `angular-frontend`). Ergänzt ADR-2026-10-21-001.

Entscheidungen:
- **Angular 22 mit Application-Builder, Standalone, strict:** `strict` und `strictTemplates` stehen explizit in `tsconfig.json` (Q-04).
- **Unit-Tests mit Vitest** über den Angular-Builder `@angular/build:unit-test` (`npm test` läuft einmalig mit `--no-watch`); kein Karma (Q-06). jsdom als Umgebung.
- **Lint:** angular-eslint mit Flat-Config (`eslint.config.js`), `npm run lint` über `ng lint`; Templates zusätzlich mit Accessibility-Regeln (Q-03).
- **Theming:** SCSS-Partials `src/styles/_variables.scss` (Bootstrap-Overrides, Marken-Gradient Lila-600/Pink-500) und `_theme.scss`; `@use` statt `@import`. Dark Mode über `data-bs-theme`, gesetzt vom `ThemeService` nach `prefers-color-scheme` (Q-01).
- **Bundle-Budgets:** Angular misst roh, Q-05 fordert gzip. Daher Warnung 600 kB / Fehler 750 kB roh für das initiale Bundle (volles Bootstrap-CSS ca. 315 kB roh, ca. 34 kB übertragen), Komponenten-Styles 2/4 kB.
- **Routing:** Lazy-Loading per `loadComponent` je Route, Wildcard leitet auf die Startseite um. Verzeichnisse `core/` und `features/<name>/` entsprechen ARC42 Abschnitt 5.
- **CI:** Eigener Workflow `frontend-angular.yml`, Trigger auf `pull_request` und `push` für `main`, `develop`, `angular-frontend` mit Pfadfilter, da die bestehenden Workflows nur `main`/`develop` auslösen. Der Pfadfilter enthält über `frontend-angular/**` hinaus `.github/workflows/frontend-angular.yml`, damit Änderungen am Workflow selbst die Pipeline auslösen und nicht ungeprüft bleiben.
- **Toolchain-Pinning:** Node 24 in der CI (`actions/setup-node`) und `packageManager: npm@11.20.0` in `package.json` legen eine reproduzierbare Toolchain fest; `npm ci` nutzt das eingecheckte Lockfile.
- **Editor-Konfiguration:** `.prettierrc` (Zeilenbreite 100, einfache Anführungszeichen, Angular-Parser für HTML), `.editorconfig` und `.vscode/` (`extensions.json`, `launch.json`, `tasks.json`) sind bewusst eingecheckt, damit Formatierung und Debug-/Task-Einstieg im Team einheitlich sind.
- **Nicht im Umfang:** BFF, Auth, Service Worker, Playwright (eigene Tickets).
