# ADR-2026-10-21-014: Angular Container-Image, E2E-Kernflüsse, Parität und Cutover (Ticket #188)

Status: umgesetzt (Branch `angular-frontend`). Ergänzt ADR-2026-10-21-007 bis -013 und ARC42 Abschnitt 7.

Entscheidungen:
- **Dockerfile in `frontend-angular/` mit drei Stufen** (Build, `npm ci --omit=dev`, Laufzeit). Der BFF wird wie im Betrieb (`npm start`) per
  `node server/index.ts` gestartet (Node 26 führt TypeScript direkt aus, `erasableSyntaxOnly`), daher kein zusätzlicher Server-Build und nur
  `express` im Laufzeit-Image. Basis `node:26-alpine` wie das Nuxt-Image, Nutzer `chronos`, Port 3000, Healthcheck auf `/api/config`
  (öffentlich, ohne Backend). Kopiert werden nur die BFF-Quellen, keine `*.spec.ts`/`test/`.
- **E2E gegen den echten BFF mit gemockter Sitzung.** Neue Specs für Login-Redirect, Termin anlegen, Freunde und Gruppen; die Sitzung
  (Cookies, `isLoggedIn`, `/api/v2/user`, Unterdrückung von Onboarding/Push-Dialog) steckt in `e2e/support/mock-session.ts` und wird auch von
  `agenda.spec.ts` genutzt (vorher dort inline). Der Service Worker ist in diesen Tests blockiert, damit `page.route` alle Anfragen sieht
  (wie bei der Agenda). Kein Mock-Server und kein Keycloak: weniger Aufbau und schnelle, deterministische Läufe; Antwortformate der Mocks folgen den Modellen in `core/models`.
- **Lighthouse über `@lhci/cli` per `npx` (festgelegte Version 0.15.1), nicht als devDependency.** Das vermeidet ein großes Lockfile-Delta für ein
  CI-Werkzeug; Konfiguration `.lighthouserc.json` (Landing, Impressum, Install; Barrierefreiheit und SEO sind Fehler, Performance und Best Practices
  Warnungen). Die Kategorie „PWA“ gibt es in Lighthouse 12+ nicht mehr; die PWA-Prüfung übernehmen die E2E-Tests für Manifest und Service-Worker-Pfad
  (REQ42 Q-02). Messwerte 2026-10-22 (lokal, simuliert mobil): Barrierefreiheit 0,89–0,94, Best Practices 1,0, SEO 0,91, Performance 0,43–0,50.
- **CI:** `frontend-angular.yml` führt Lighthouse nach den E2E-Tests aus (Chromium-Pfad aus Playwright) und baut das Image in einem eigenen Job ohne Push.
  Veröffentlicht wird das Image erst beim Cutover (`ARC42-angular-cutover.md`), damit `main` und die Registry unverändert bleiben.
- **Paritätsliste und Cutover-Anleitung** liegen als `kb/architecture/ARC42-angular-parity.md` und `ARC42-angular-cutover.md`. Lücken (u. a. `robots.txt`,
  Performance) sind dort vermerkt und werden nicht in #188 nachgebaut (Vorgabe des Orchestrators).
