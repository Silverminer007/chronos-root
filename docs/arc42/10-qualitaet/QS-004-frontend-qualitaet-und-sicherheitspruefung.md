---
id: QS-004
title: "Frontend-Qualität und Sicherheitsprüfung"
status: entwurf
typ: qualitaetsszenario
quellen:
  - .github/workflows/frontend-ci.yml
verknuepft: [QA-002, QA-003]
---

# QS-004: Frontend-Qualität und Sicherheitsprüfung

**Szenario:** Ein PR ändert `frontend/`. **Erwartung:** gitleaks, `npm audit` (high), ESLint, Typecheck, Build und E2E-Tests (`e2e/landing.spec.ts`, `public-pages.spec.ts`, `auth-redirects.spec.ts`) sind grün. **Nachweis:** Jobs in `frontend-ci.yml`.
