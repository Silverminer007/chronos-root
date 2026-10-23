# ARC42: Cutover Nuxt → Angular (Anleitung)

Stand: 2026-10-22 (Ticket #188). Gilt für den Wechsel des Produktiv-Frontends von `frontend/` (Nuxt) auf `frontend-angular/`.
Der Cutover ist **nicht** Teil von #188; `main` und das Helm-Chart bleiben bis dahin unverändert. Voraussetzung: offene Punkte der
Paritätsliste (`ARC42-angular-parity.md`, Abschnitt „Bekannte Lücken“) sind entschieden, mindestens `robots.txt`.

## Das Image

`frontend-angular/Dockerfile` (drei Stufen: Build, Produktions-Dependencies, Laufzeit). Das Laufzeit-Image enthält `dist/browser`, den
BFF (`server/*.ts`, von Node direkt ausgeführt) und `express`. Läuft als Nutzer `chronos` (UID 1001), lauscht auf Port **3000** (wie Nuxt),
Healthcheck `GET /api/config`. Lokal:

```bash
docker build -t chronos-frontend-angular frontend-angular
docker run --rm -p 3000:3000 \
  -e CHRONOS_AUTH_ISSUER=https://keycloak/realms/chronos -e CHRONOS_AUTH_CLIENT_ID=chronos-frontend \
  -e CHRONOS_AUTH_REDIRECT_URI=http://localhost:3000/api/auth/callback -e CHRONOS_QUARKUS_URL=http://backend:8080 \
  -e CHRONOS_COOKIE_SECURE=false chronos-frontend-angular
```

Ohne TLS (lokal) muss `CHRONOS_COOKIE_SECURE=false` gesetzt sein, sonst setzt der Browser die Sitzungs-Cookies nicht.

## Env-Mapping

Der BFF liest `CHRONOS_<NAME>` und fällt auf `NUXT_<NAME>` zurück (`server/config.ts`). Das Chart kann daher **unverändert** bleiben.

| Nuxt (Helm `frontend.env` / `envFromSecrets`) | Angular (bevorzugt) | Pflicht |
|---|---|---|
| `NUXT_AUTH_ISSUER` | `CHRONOS_AUTH_ISSUER` | ja |
| `NUXT_AUTH_CLIENT_ID` (Secret `frontend_client_id`) | `CHRONOS_AUTH_CLIENT_ID` | ja |
| `NUXT_AUTH_REDIRECT_URI` | `CHRONOS_AUTH_REDIRECT_URI` | ja |
| `NUXT_QUARKUS_URL` | `CHRONOS_QUARKUS_URL` | ja |
| `NUXT_PUBLIC_IMPRESSUM_{NAME,STREET,CITY,EMAIL,PHONE}` | `CHRONOS_PUBLIC_IMPRESSUM_*` | nein |
| – | `CHRONOS_COOKIE_SECURE` (Standard `true`) | nein |
| – | `CHRONOS_PORT` (Standard 3000; `PORT` wird ebenfalls gelesen), `CHRONOS_STATIC_DIR` | nein |
| `NUXT_PUBLIC_SENTRY_DSN`, `SENTRY_AUTH_TOKEN` | entfallen (werden ignoriert) | nein |

Fehlt eine Pflichtvariable, beendet sich der Prozess beim Start mit der Liste der fehlenden Namen (Exit 1). Die Impressum-Werte liefert
der BFF per `GET /api/config`.

## Schritte

1. **Image veröffentlichen.** Den Build-/Push-Job aus `.github/workflows/frontend-ci.yml` auf `frontend-angular` umstellen
   (`working-directory`, `paths`, `docker build -f Dockerfile .` im Verzeichnis `frontend-angular`). Der Repository-Name
   `ghcr.io/<owner>/chronos-frontend` bleibt, damit `deployment/values*.yaml` nicht geändert werden muss. Die Deployment-Auslösung
   (`repository_dispatch` mit `service: frontend`) bleibt gleich. Bis zu diesem Schritt baut der Job `image` in
   `.github/workflows/frontend-angular.yml` das Image nur zur Prüfung, ohne Push.
2. **Staging zuerst.** Auf `develop` mergen, Staging deployt `chronos-frontend:develop`. Prüfen: Login über Keycloak, Agenda, Termin anlegen,
   Freunde, Gruppen, Push-Abo, Installierbarkeit (Manifest) und `/public/impressum`.
3. **Produktion.** `angular-frontend` nach `main` mergen. Der Image-Tag `latest` zeigt danach auf das Angular-Image; das Chart-`frontend`-Deployment
   rollt neu aus (`pullPolicy: Always`). Replikas, Ressourcen, Service (Port 80 → 3000) und Ingress bleiben unverändert.
4. **Alte Variablen aufräumen** (optional, nach einer stabilen Woche): `NUXT_*` in den Werte-Dateien durch `CHRONOS_*` ersetzen, die
   Sentry-Einträge entfernen.
5. **Nuxt entfernen**: `frontend/` und `frontend-ci.yml` löschen, `frontend-angular/` nach `frontend/` umbenennen, CLAUDE.md und
   Workflow-Pfade nachziehen (eigenes Ticket, nicht Teil des Cutovers selbst).

## Was Nutzer beim Wechsel merken

- **Sitzungen bleiben gültig:** gleiche Cookies (`kc_access`, `kc_refresh`, `kc_expires`), gleiche Keycloak-Anbindung, gleiche Callback-URL.
- **Service Worker** wird unter demselben Pfad `/push-sw.js` ersetzt (Scope `/`); bestehende Push-Abos bleiben bestehen.
- **localStorage-Schlüssel** (`onboardingShownAt`, `shownFeatureIds`, `pushPromptAnswer`, `pushPromptShownAt`) sind identisch; Onboarding und
  Neuigkeiten erscheinen nicht erneut.
- Oberfläche und Icons sehen anders aus (Bootstrap statt PrimeVue/Tailwind); Verhalten ist gleich, Abweichungen siehe Paritätsliste.

## Rollback

Image-Tag `latest` bzw. `develop` auf das letzte Nuxt-Image zurücksetzen (Tag der letzten Nuxt-Version aus dem Registry-Verlauf) und
`kubectl rollout restart deployment/chronos-frontend -n <namespace>`. Env-Variablen müssen dafür nicht geändert werden, weil `NUXT_*` beibehalten wurde.
Backend und Datenbank sind vom Cutover nicht betroffen (keine API-Änderung).

## Prüfungen vor dem Cutover

- `cd frontend-angular && npm ci && npm run lint && npm test && npm run build` grün.
- `npm run test:e2e` grün (Chromium und Pixel 5), `npm run test:lhci` ohne Fehler (Barrierefreiheit ≥ 0,85, SEO ≥ 0,9).
- `docker build` und Start mit den vier Pflichtvariablen: `GET /` → 200, `GET /agenda` ohne Cookies → 301 auf `/`, `GET /api/config` → 200.
