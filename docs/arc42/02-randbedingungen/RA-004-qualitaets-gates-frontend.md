---
id: RA-004
title: "Qualitäts-Gates Frontend"
status: entwurf
typ: randbedingung
quellen:
  - .github/workflows/frontend-ci.yml
  - frontend/code-style.md
verknuepft: [QA-002]
---

# RA-004: Qualitäts-Gates Frontend

Die Frontend-CI führt Secret-Detection (gitleaks), `npm audit --audit-level=high`, `npm run lint`, `npm run typecheck`, den Build und Playwright-E2E-Tests aus. Code-Stil: `frontend/code-style.md`; `<script setup lang="ts">` überall, nie Options API.
