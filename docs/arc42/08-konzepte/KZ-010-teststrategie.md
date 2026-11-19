---
id: KZ-010
title: Teststrategie und Testdaten
status: entwurf
typ: konzept
quellen:
  - kb/constraints/002-testing-requirements.md
  - kb/adr/ADR-2026-10-20-025-test-db-template-and-cleanup.md
  - CLAUDE.md
verknuepft: [QA-001, QA-002, BB-010]
---

# Teststrategie und Testdaten

- **Java:** JUnit mit Testcontainers (PostgreSQL), Fixtures in `src/test/resources/db/testdata/`, `./mvnw test`/`verify`.
- **Rust:** Unit-Tests (`--lib`) und Integrationstests mit echter Datenbank; Test-DBs werden aus einem Template geklont und von einem Janitor entfernt (ADR-025); Hilfen in `src/test_utils`.
- **Frontend:** Playwright-E2E ohne laufendes Backend (`e2e/landing`, `public-pages`, `auth-redirects`), Lighthouse CI.
- **Contract:** Parität Java ↔ Rust (BB-010). **Last:** `load-tests/staging.js`.
