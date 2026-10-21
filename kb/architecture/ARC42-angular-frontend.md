# ARC42: Angular-Frontend

Branch: `angular-frontend`. Stand: 2026-10-21. Anforderungen: `kb/requirements/REQ42-angular-frontend.md`.

## 1. Einführung und Ziele

Ersatz des Nuxt-Frontends durch Angular + SCSS + Bootstrap bei unveränderter Backend-API (siehe REQ42 Abschnitt 1, 3, 4).

## 2. Randbedingungen

Angular Standalone/Signals, SCSS, Bootstrap 5, Deutsch, Same-Origin-API, keine Backend-Änderung (REQ42 Abschnitt 5).

## 3. Kontextabgrenzung

```
Browser (Angular SPA + Service Worker)
   │  same-origin: /api/auth/*, /api/v2/*
   ▼
BFF (Node/Express, frontend-angular/server)  ──►  Quarkus / Rust Backend (/api/v2/*, Bearer)
   │                                         ──►  Keycloak (OIDC Code Flow, Token, Refresh)
   └── liefert statische SPA (dist/browser) und Cookies
```

## 4. Lösungsstrategie

| Entscheidung | Begründung |
|---|---|
| Angular-Standalone-Komponenten, Lazy-Loading je Route | kleines Initial-Bundle (Q-05) |
| Zustand in Signal-basierten Services (`*Store`) statt NgRx | gleiche Größe wie bisherige Pinia-Stores (~1500 Zeilen), geringere Komplexität |
| Bootstrap 5 + eigene SCSS-Variablen/Theme (Lila/Pink-Gradient als Markenfarbe) | Vorgabe; Dark Mode über `data-bs-theme` |
| Eigener schlanker BFF in Node statt Angular SSR | Das Nuxt-Frontend ist SPA + Serverrouten; Cookie-/Token-Logik (httpOnly) braucht einen Serverteil. SSR ist nicht gefordert |
| Service Worker bleibt handgeschrieben (`push-sw.js` portiert) | Token-Refresh und Push sind Kernlogik; `@angular/service-worker` deckt das Verhalten nicht ab |

## 5. Bausteinsicht

```
frontend-angular/
├── server/                  BFF: auth routes, /api/v2 proxy, static hosting, config
├── src/app/
│   ├── core/                AuthService, authGuard, HTTP-Interceptor, Fehlerbehandlung, Modelle (types)
│   ├── shared/              wiederverwendbare Komponenten (Card, Dialog, Skeleton, Confirm), Pipes (Datum)
│   ├── layout/              DefaultLayout (authentifiziert), LandingLayout
│   ├── features/
│   │   ├── public/          Landing, Install, Impressum, Datenschutz
│   │   ├── agenda/          Terminliste
│   │   ├── appointment/     Details, Anlegen/Bearbeiten, Teilnehmer, Nachrichten
│   │   ├── friends/  groups/  settings/  profile/  onboarding/  survey/  admin/
│   └── app.routes.ts
├── public/                  Manifest, Icons, push-sw.js
└── e2e/                     Playwright
```

Zuordnung alt → neu: Pinia-Store → `*Store`-Service (Signals); `useFetch` → `HttpClient` in Services; `middleware/auth.global.ts` → `authGuard`;
`layouts/*.vue` → Layout-Komponenten; PrimeVue-Dialoge → Bootstrap-Modal (eigene `ModalService`/Komponente); Toast → Bootstrap-Toast-Service.

Bausteine in `core/` (siehe ADR-2026-10-21-004): `ToastService` (Signal-Liste) und `ModalService` (`confirm()` liefert `Promise<boolean>`) halten den Zustand;
`ToastContainerComponent` und `ConfirmDialogComponent` stellen ihn dar und sind je einmal in `App` eingebunden. `OnboardingService` (Port von `useOnboarding`,
localStorage-Schlüssel `onboardingShownAt`) wird vom `authGuard` für die Weiterleitung auf `/onboarding` befragt; `errorInterceptor` nutzt den `ToastService`.

## 6. Laufzeitsicht

1. **Login:** `/` → Button → `GET /api/auth/login` → Keycloak → `GET /api/auth/callback` setzt `kc_access`, `kc_refresh` (httpOnly), `kc_expires` → Redirect `/agenda`.
2. **Navigation:** `authGuard` fragt `GET /api/auth/isLoggedIn` (vom Service Worker synthetisch beantwortet); ungültig → `/`, Profil unvollständig → `/onboarding`.
3. **API-Aufruf:** Service → `HttpClient` → `/api/v2/...` → Service Worker erneuert Token bei < 5 s Restlaufzeit → BFF hängt `Authorization: Bearer` an → Backend. Bei 401 einmaliger Retry nach Refresh.
4. **Sitzungsende:** `kc_expires` verschwindet → SW sendet `SESSION_EXPIRED` → `AuthService` navigiert auf `/`.
5. **Push:** Abo über `PushService`; SW zeigt Benachrichtigung, Klick fokussiert/öffnet `/appointment/:id`.

## 7. Verteilungssicht

Ein Container-Image (Node-Laufzeit, BFF + statische Dateien), Port 3000. Helm-Chart in `deployment/` verweist auf das neue Image erst beim Cutover.
Umgebungsvariablen: `CHRONOS_AUTH_ISSUER`, `CHRONOS_AUTH_CLIENT_ID`, `CHRONOS_AUTH_REDIRECT_URI`, `CHRONOS_QUARKUS_URL`, `CHRONOS_PUBLIC_IMPRESSUM_*`
(mit Fallback auf die alten `NUXT_*`-Namen, damit das Chart unverändert bleibt).

## 8. Querschnittliche Konzepte

- **Fehlerbehandlung:** zweistufig, siehe ADR-2026-10-21-004.
  - `errorInterceptor` zeigt nur auf Anforderung (`HttpContextToken` `GLOBAL_ERROR_TOAST`, Standard `false`) einen festen deutschen Toast für übergreifende Fehler: Status 0 (keine Verbindung), 403 und ≥ 500. 401 und `/api/auth/isLoggedIn` ignoriert er (Token-Erneuerung und Sitzungsende liegen beim Service Worker bzw. `AuthService`). Der Fehler wird unverändert weitergeworfen.
  - 4xx wie 400/404/409 behandeln die Aufrufer selbst: `getErrorMessage(err, fallback)` (`core/errors.ts`, Port von `utils/errors.ts`) liefert die Backend-Meldung oder den deutschen Fallback, `getErrorStatus(err)` den HTTP-Status zur Fallunterscheidung. Die englische `HttpErrorResponse.message` wird nie angezeigt.
- **Datum/Zeit:** Luxon bleibt (`DateFormatterService`).
- **Theming:** SCSS-Partials `_variables.scss`, `_theme.scss`; Bootstrap-Variablen überschrieben vor dem Import; Dark Mode via `data-bs-theme`.
- **Typen:** `core/models` ist 1:1 Port von `app/types/index.ts`.
- **Tests:** Unit-Tests je Service/Store; Playwright für Redirects, öffentliche Seiten, PWA-Manifest und Kernflüsse mit gemocktem BFF.
- **Sicherheit:** Tokens nie in JS-erreichbarem Speicher; CSRF-Schutz durch `SameSite=Lax` und reine JSON-API.

## 9. Architekturentscheidungen

- ADR-2026-10-21-001: Angular statt Nuxt (diese Migration). Kein NgRx, eigener Node-BFF, handgeschriebener Service Worker.
- ADR-2026-10-21-004: `core/` (Modelle, AuthService, authGuard, Interceptor, Toast, Modal).

## 10. Qualitätsanforderungen

Siehe REQ42 Abschnitt 4; Umsetzung: Bundle-Budgets in `angular.json`, ESLint (angular-eslint), CI-Workflow `frontend-angular.yml`.

## 11. Risiken und technische Schulden

| Risiko | Gegenmaßnahme |
|---|---|
| Funktionsabweichung zum Nuxt-Frontend | je Feature Paritätsliste im Ticket, E2E |
| Service-Worker-Portierung (Cookie-Store-API, Refresh-Dedup) | eigenes Arbeitspaket, Port 1:1 mit Tests |
| Parallelbetrieb zweier Frontends bis Cutover | getrennte Verzeichnisse, getrennte CI |

## 12. Glossar

BFF = Backend for Frontend (Node-Proxy). SW = Service Worker. OIDC = OpenID Connect.

## Nachtrag 2026-10-21: Ergänzung zu Abschnitt 9 (Architekturentscheidungen)

Die KB ist unveränderlich, daher steht die Ergänzung als Nachtrag. Abschnitt 9 nennt nur ADR-2026-10-21-001; weitere maßgebliche Entscheidungen:

- ADR-2026-10-21-002 (Ticket #176, Projektgerüst): Angular 22 mit Application-Builder, Standalone, strict; Vitest statt Karma; angular-eslint (Flat-Config); SCSS-Partials mit Dark Mode über `data-bs-theme`; Bundle-Budgets roh gemessen (Warnung 600 kB, Fehler 750 kB; Q-05 fordert gzip); Lazy-Loading je Route; eigener CI-Workflow `frontend-angular.yml`; Node 24 und `packageManager` gepinnt.
- ADR-2026-10-21-003 (Ticket #177, BFF-Server): Express 5 mit eingebautem `fetch`, TypeScript ohne Build-Schritt (`node server/index.ts`); Port der Nuxt-Routen (`/api/auth/*`, Catch-all `/api/v2/*`), statisches Hosting von `dist/browser` mit SPA-Fallback, 301-Redirect auf `/` ohne Auth-Cookie, Konfiguration über `CHRONOS_*` mit Fallback auf `NUXT_*`, neuer Endpoint `GET /api/config` für die Impressum-Werte. Abweichungen vom Nuxt-Verhalten:
  1. `SameSite=Lax` statt `None`.
  2. `Secure` per `CHRONOS_COOKIE_SECURE=false` abschaltbar (Standard `true`).
  3. Fehlender `code` im Callback gibt 400, Keycloak-/Token-Fehler 502 (Nuxt: ungefangener 500er bzw. leere Antwort).
  4. Ohne `kc_access` sendet der Proxy keinen `Authorization`-Header (Nuxt: `Bearer undefined`).
  5. Der Proxy reicht Status und Body des Backends unverändert durch (Nuxt: eigene Fehlerobjekte von `$fetch`); Backend nicht erreichbar ergibt 502.
  6. `refresh` und `isLoggedIn` antworten 204; der Browser-Cookie-Header wird nicht ans Backend weitergegeben.

## Nachtrag 2026-10-21: Ergänzung zu Abschnitt 6 (Laufzeitsicht) und 7 (Verteilungssicht)

Die KB ist unveränderlich, daher stehen die Ergänzungen als Nachtrag. Sie gelten zusätzlich zu den Abschnitten 6 und 7 und zum ADR-2026-10-21-003 (Ticket #177).

**Routen und Datenflüsse (Ergänzung zu Abschnitt 6):**

- `GET /api/config` liefert die Impressum-Werte (`CHRONOS_PUBLIC_IMPRESSUM_*`) als JSON an die SPA. Die Werte werden zur Laufzeit gelesen, nicht zur Build-Zeit; der Endpoint braucht keine Anmeldung. Der Abruf durch die Impressum-Seite folgt mit dem Ticket für die öffentlichen Seiten.
- Weitere BFF-Routen: `GET /api/auth/register`, `GET /api/auth/login|callback|isLoggedIn`, `POST /api/auth/refresh|logout`, Catch-all `/api/v2/*` (Proxy mit `Authorization: Bearer` aus `kc_access`). Unbekannte `/api/*`-Pfade ergeben 404 und fallen nicht auf die SPA zurück.
- Statische Auslieferung: GET/HEAD-Anfragen ohne Sitzungs-Cookie (`SESSION_COOKIES`) auf Seiten außer `/` und `/public/*` werden mit 301 auf `/` umgeleitet; Dateien mit Endung (Bundles, Manifest, Service Worker) bleiben erreichbar. Alle übrigen Pfade erhalten den SPA-Fallback (`index.html`).

**Betrieb und Konfiguration (Ergänzung zu Abschnitt 7):**

| Variable | Bedeutung | Standard |
|---|---|---|
| `CHRONOS_PORT` (Fallback `PORT`) | Listen-Port des BFF | `3000` |
| `CHRONOS_STATIC_DIR` | Verzeichnis der statischen SPA | `dist/browser` relativ zu `frontend-angular/` |
| `CHRONOS_COOKIE_SECURE` | `false` schaltet das `Secure`-Attribut der Cookies ab (lokale HTTP-Entwicklung) | `true` |

- Alle `CHRONOS_AUTH_*`, `CHRONOS_QUARKUS_URL` und `CHRONOS_PUBLIC_IMPRESSUM_*`-Werte fallen auf den gleichnamigen `NUXT_*`-Wert zurück, wenn die `CHRONOS_*`-Variable fehlt.
- Pflichtwerte sind `CHRONOS_AUTH_ISSUER`, `CHRONOS_AUTH_CLIENT_ID`, `CHRONOS_AUTH_REDIRECT_URI` und `CHRONOS_QUARKUS_URL` (jeweils auch als `NUXT_*`). Fehlt einer, gibt der BFF `Fehlende Konfiguration: <Namen>` aus und beendet sich mit Exit-Code 1.
- Start: `npm start` startet den BFF (`node server/index.ts`, TypeScript ohne Build-Schritt per Node-Type-Stripping, Node 24; vorher `npm run build`). `npm run start:dev` ist `ng serve` (Port 4200) und startet keinen BFF.

## Nachtrag 2026-10-21: Ergänzung zu Abschnitt 8 (Querschnittliche Konzepte, Sicherheit)

Die KB ist unveränderlich, daher steht die Ergänzung als Nachtrag. Abschnitt 8 nennt für die Sicherheit nur `SameSite=Lax`; zusätzlich gilt für den BFF (Ticket #177, ADR-2026-10-21-003):

- **Cookies:** `kc_access` und `kc_refresh` sind httpOnly, `kc_expires` ist für den Client lesbar. `SameSite=Lax` ist eine bewusste Abweichung von Nuxt (`SameSite=None`), weil SPA und BFF same-origin ausgeliefert werden. `Secure` ist Standard; `CHRONOS_COOKIE_SECURE=false` schaltet es nur für lokale HTTP-Entwicklung ab (siehe Nachtrag zu Abschnitt 7).
- **Fehlercodes:** Fehlender `code` im Callback ergibt 400, Keycloak-/Token-Fehler und ein nicht erreichbares Backend 502, unbekannte `/api/*`-Pfade 404 als JSON. Ohne `kc_access` sendet der Proxy keinen `Authorization`-Header (kein `Bearer undefined`); das Backend antwortet dann 401.
- Die vollständige Liste der Abweichungen und ihre Begründung stehen im ADR-2026-10-21-003.
