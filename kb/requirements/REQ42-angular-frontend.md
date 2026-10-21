# REQ42: Angular-Frontend (Migration von Nuxt/Vue)

Branch: `angular-frontend` (Integrationsbranch, nicht `main`). Stand: 2026-10-21.

## 1. Kontext und Ziel

Das bestehende Frontend (`frontend/`, Nuxt 4 + Vue 3 + PrimeVue + Tailwind) wird durch eine Angular-Anwendung mit
SCSS und Bootstrap 5 ersetzt. Funktionsumfang und Backend-API (`/api/v2/*`, Quarkus oder Rust) bleiben unverändert.
Die gesamte Oberfläche bleibt Deutsch.

**Nicht-Ziele:** Neue Funktionen, API-Änderungen, Backend-Änderungen, Migration auf `main` (separate Entscheidung).

## 2. Stakeholder

| Rolle | Interesse |
|---|---|
| Nutzer (Jugendgruppen, Leiter) | Gleiche Funktionen, mobil nutzbar, Push-Benachrichtigungen |
| Admin | Admin-Seiten (Push, Übersicht) |
| Entwickler | Etablierter Stack, Tests, wartbarer Code |
| Betrieb | Container-Image, Helm-Chart, gleiche Umgebungsvariablen |

## 3. Funktionale Anforderungen

| ID | Anforderung |
|---|---|
| F-01 | Login/Logout per Keycloak OIDC (Authorization Code); Tokens nur als httpOnly-Cookies; `kc_expires` lesbar |
| F-02 | Automatische Token-Erneuerung (Service Worker + Proxy), Weiterleitung auf `/` bei abgelaufener Sitzung |
| F-03 | Routenschutz: ohne Sitzung Redirect auf `/`; unvollständiges Profil Redirect auf `/onboarding` |
| F-04 | Öffentliche Seiten: Landingpage, Installationshinweis, Impressum (aus Umgebungsvariablen), Datenschutz |
| F-05 | Terminübersicht (Agenda) mit Suche, Paginierung und Skeleton-Ladezustand |
| F-06 | Termin anlegen (Stepper), bearbeiten, absagen, löschen, verschieben |
| F-07 | Termindetails: Teilnehmer, Gruppen, eigene Teilnahme (zu-/absagen), Rollen ändern, Teilnehmer hinzufügen/entfernen, Nachrichten, Teilen |
| F-08 | Freunde: Liste, Anfragen senden/annehmen/ablehnen, Vorschläge, entfernen |
| F-09 | Gruppen: Liste, anlegen, Details, Mitglieder hinzufügen/entfernen |
| F-10 | Einstellungen: Benachrichtigungen je Termin/Gruppe, Push aktivieren, Profil |
| F-11 | Onboarding, „Neue Funktionen“-Dialog, PWA-Installationsdialog |
| F-12 | Umfrage-Seite `/survey/:id` |
| F-13 | Admin-Bereich `/admin`, `/admin/push` (nur Admin-Rolle) |
| F-14 | Web-Push: Service Worker empfängt Push, zeigt Benachrichtigung, Klick öffnet Termin |

## 4. Qualitätsanforderungen

| ID | Anforderung | Messung |
|---|---|---|
| Q-01 | Darstellung ab 320 px Breite (mobile first), Dark Mode über `prefers-color-scheme` | Playwright-Viewport 360x740 |
| Q-02 | PWA installierbar, Lighthouse PWA-Kriterien erfüllt | Manifest + Service Worker vorhanden |
| Q-03 | Barrierefreiheit: Formularlabels, Fokus, Kontrast (Bootstrap-Standard) | axe-Stichprobe in E2E |
| Q-04 | Typsicherheit: `strict` TypeScript, ESLint ohne Fehler | CI |
| Q-05 | Initiales JS-Bundle der Startseite < 300 kB gzip (Lazy-Loading je Route) | `ng build` Budget |
| Q-06 | Tests: Unit-Tests (Vitest/Jest oder Karma) je Service/Store, E2E (Playwright) für Kernflüsse | CI grün |
| Q-07 | Konfiguration ausschließlich über dieselben Umgebungsvariablen wie bisher (`NUXT_*` werden zu `CHRONOS_*` mit Fallback) | Dokumentiert in ARC42 |

## 5. Randbedingungen

- Angular (aktuelle LTS/Stable), Standalone Components, Signals, SCSS, Bootstrap 5 (`bootstrap` npm, kein ng-bootstrap-Zwang).
- Kein Tailwind, kein PrimeVue. Icons: Bootstrap Icons.
- Clientseitig nur Aufrufe an `/api/v2/*` und `/api/auth/*` desselben Ursprungs; der Client spricht nie direkt mit Quarkus/Keycloak.
- Deutsch in allen UI-Texten und Fehlermeldungen.
- Das alte Frontend bleibt bis zum Cutover unverändert unter `frontend/`; das neue liegt unter `frontend-angular/`.

## 6. Abnahmekriterien

1. Alle F-Anforderungen sind mit einem E2E- oder Unit-Test abgedeckt.
2. `npm run lint`, `npm test`, `npm run build` sind grün; CI-Workflow `frontend-angular` läuft nur bei Änderungen im Verzeichnis.
3. Das Container-Image startet mit denselben Umgebungsvariablen und besteht den Smoke-Test (Landing, Redirect, PWA-Manifest).
4. Kein Rückgriff auf Nuxt/Vue/Tailwind/PrimeVue in `frontend-angular/`.
