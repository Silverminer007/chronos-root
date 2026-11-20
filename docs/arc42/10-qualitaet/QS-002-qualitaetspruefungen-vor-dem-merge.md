---
id: QS-002
title: "Qualitätsprüfungen vor dem Merge"
status: entwurf
typ: qualitaetsszenario
quellen:
  - .github/workflows/backend-ci.yml
  - .github/workflows/frontend-ci.yml
  - backend/pom.xml
  - frontend/e2e/
  - frontend/playwright.config.ts
verknuepft: [RB-008, RA-004, TD-004]
---

# QS-002: Qualitätsprüfungen vor dem Merge

Welche Prüfungen es gibt, legen [RB-008](../../req42/07-randbedingungen/RB-008-qualitaetspruefungen-vor-dem-merge.md) und [RA-004](../02-randbedingungen/RA-004-qualitaets-gates-frontend.md) fest; hier steht, was sie messen und wo sie Lücken lassen.

| | |
|---|---|
| **Reiz** | Ein Pull Request oder Push auf `main`/`develop` ändert `backend/` oder `frontend/`. |
| **Antwort** | Backend: Job `test` (`./mvnw test`, Testcontainers) und Job `quality` (`./mvnw verify -DskipTests`: Checkstyle mit `failsOnError`, SpotBugs mit Aufwand `Max` und Schwelle `Medium`, PMD mit `failOnViolation` einschließlich Testcode). Frontend: Secret-Scan, `npm audit --audit-level=high`, Lint, Typecheck, Build, Playwright-E2E gegen den gebauten Nitro-Server ohne Backend (Projekte `chromium` und `mobile-chrome`; `landing.spec.ts`, `public-pages.spec.ts`, `auth-redirects.spec.ts`). Images werden erst nach grünen Jobs gebaut. |
| **Messung** | Alle Jobs grün, 0 Verstöße, keine bekannte Schwachstelle ab Stufe „high“. Es gibt keine Mindest-Testabdeckung (JaCoCo erzeugt nur einen Bericht). |
| **Nachweis** | CI-Jobs. Lücken: Merge-Sperre bei rotem Job hängt am Branch-Schutz **(?)**; keine Unit-/Store-Tests im Frontend ([TD-004](../11-risiken/TD-004-frontend-unit-tests-fehlen.md)); keine E2E-Tests für angemeldete Abläufe. |
