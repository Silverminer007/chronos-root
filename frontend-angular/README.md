# frontend-angular

Angular-Frontend von Chronos (Ersatz für `../frontend`, Nuxt). Anforderungen: `kb/requirements/REQ42-angular-frontend.md`,
Architektur: `kb/architecture/ARC42-angular-frontend.md`. Die Oberfläche ist komplett Deutsch.

**Stack:** Angular (Standalone, Signals, strict TypeScript), SCSS, Bootstrap 5 + Bootstrap Icons, Vitest (über `ng test`), angular-eslint.

## Befehle

```bash
npm ci
npm start        # Dev-Server auf :4200
npm run lint     # angular-eslint
npm test         # Unit-Tests (Vitest, einmalig)
npm run build    # Produktions-Build inkl. Bundle-Budgets nach dist/
```

## Aufbau

```
src/
├── app/
│   ├── core/              Services (z. B. ThemeService)
│   ├── features/<name>/   je Feature eine lazy geladene Komponente
│   └── app.routes.ts      Routing (loadComponent je Route, Wildcard → Startseite)
└── styles/
    ├── _variables.scss    Bootstrap-Variablen und Markenfarben (Lila/Pink), vor Bootstrap eingebunden
    └── _theme.scss        Gradient-Klassen (`text-gradient`, `bg-gradient-brand`, `btn-gradient`)
```

## Theming und Dark Mode

Bootstrap-Variablen werden in `_variables.scss` überschrieben und über `@use` vor Bootstrap geladen (`src/styles.scss`).
Der `ThemeService` setzt `data-bs-theme` (`light`/`dark`) auf `<html>` nach `prefers-color-scheme` und folgt Änderungen.

## Bundle-Budgets

In `angular.json` (Produktion): initiales Bundle Warnung 600 kB / Fehler 750 kB roh (aktuell ca. 87 kB übertragen, Ziel Q-05: < 300 kB gzip);
Komponenten-Styles Warnung 2 kB / Fehler 4 kB.

## CI

`.github/workflows/frontend-angular.yml` führt bei Änderungen in diesem Verzeichnis `lint`, `test` und `build` aus
(Branches `main`, `develop`, `angular-frontend`).
