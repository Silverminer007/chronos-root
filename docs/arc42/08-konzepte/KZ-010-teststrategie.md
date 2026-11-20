---
id: KZ-010
title: Teststrategie und Testdaten
status: entwurf
typ: konzept
quellen:
  - backend/src/test
  - backend/pom.xml
  - backend/performance-tests/README.md
  - frontend/playwright.config.ts
  - frontend/e2e
  - frontend/.lighthouserc.json
  - .github/workflows/backend-ci.yml
verknuepft: [RB-008, RA-004]
---

# Teststrategie und Testdaten

- **Backend:** JUnit 5 mit `quarkus-junit`, Mockito, AssertJ. 36 Testklassen: Unit-Tests für Services und Reminder-Regeln, Ressourcen- und Integrationstests (`BaseIntegrationTest`, Concurrency, Fehlerfälle). Datenbank per Testcontainers (Quarkus Dev Services, PostgreSQL); `test`-Konfiguration: Flyway mit `clean-at-start` und Pfaden `db/migration` plus `db/testdata` (`V99.0.0__test_fixtures.sql`), OIDC aus (`MockJsonWebTokenProducer`), Keycloak-Dev-Services aus, `scheduler.leader-election.enabled=false`. CI: `./mvnw test` mit `TESTCONTAINERS_RYUK_DISABLED`; JaCoCo erzeugt einen Bericht ohne Mindestwert. `ErrorHandlingIntegrationTest` und `ConcurrencyIntegrationTest` rufen die Pfade `…/participants/approve` und `…/participants/users` auf, die das Backend nicht anbietet ([BB-006](../05-bausteine/BB-006-java-schichten.md)); ob diese Tests dennoch grün sind, folgt aus dem Code nicht **(?)**.
- **Frontend:** Playwright-E2E (`e2e/auth-redirects`, `landing`, `public-pages`) in Chromium und Pixel-5-Emulation gegen den gebauten Server mit Platzhalter-Konfiguration und ohne Backend; JUnit-Bericht in der CI. Keine Unit-Tests. `.lighthouserc.json` (Schwellwerte für Performance, Accessibility, SEO, PWA) existiert, ein Workflow führt sie nicht aus.
- **Last:** k6-Suite `backend/performance-tests` (Szenarien je Ressource, Docker-Umgebung mit Keycloak, Skript `scripts/run-perf-tests.sh`, Standard 50 virtuelle Nutzer, 2 min); nicht in der CI.
- **Pflicht:** Neue Funktionen kommen mit Tests (RB-008); Qualitäts-Gates: [RA-004](../02-randbedingungen/RA-004-qualitaets-gates-frontend.md), RB-008.
