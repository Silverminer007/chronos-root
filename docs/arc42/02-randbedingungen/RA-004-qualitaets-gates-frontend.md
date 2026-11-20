---
id: RA-004
title: "Qualitäts-Gates und Code-Stil Frontend"
status: entwurf
typ: randbedingung
quellen:
  - .github/workflows/frontend-ci.yml
  - frontend/code-style.md
  - frontend/package.json
verknuepft: [RB-008, VT-004, KZ-010]
---

# RA-004: Qualitäts-Gates und Code-Stil Frontend

Jeder PR, der `frontend/` ändert, durchläuft in `frontend-ci.yml`: Secret-Detection (gitleaks über die volle Historie), `npm audit --audit-level=high`, `npm run lint` (ESLint), `npm run typecheck` (`nuxi typecheck`), `npm run build` und Playwright-E2E ([KZ-010](../08-konzepte/KZ-010-teststrategie.md)). Docker-Build und Push folgen nur nach grünen Gates auf `main`/`develop` ([VT-004](../07-verteilung/VT-004-ci-cd-kette.md)).

Code-Stil (`frontend/code-style.md`): Composition API mit `<script setup lang="ts">`, nie Options API in Komponenten; Blockreihenfolge `script`, `template`, `style scoped`; Props und Emits typisiert; Typimporte mit `import type`; Alias `~/` für `app/`; Auto-Imports für Vue- und Nuxt-Composables, Stores und lokale Composables; Tailwind vor eigenem CSS. Die Backend-Gates (Checkstyle, SpotBugs, PMD) sind in RB-008 festgelegt.
