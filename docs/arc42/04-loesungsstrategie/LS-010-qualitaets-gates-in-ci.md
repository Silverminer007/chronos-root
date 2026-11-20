---
id: LS-010
title: Automatische Qualitäts-Gates
status: entwurf
typ: strategie
quellen:
  - .github/workflows
  - backend/pom.xml
  - scripts/check-no-local-settings.sh
verknuepft: [RA-004, RB-008, RB-011, VT-004]
---

# Automatische Qualitäts-Gates

Jeder Pull Request durchläuft automatische Prüfungen, die ohne menschliches Zutun blockieren: Backend: Tests sowie Checkstyle, SpotBugs und PMD (RB-008); Frontend: Secret-Detection, Audit, Lint, Typecheck, Build und E2E ([RA-004](../02-randbedingungen/RA-004-qualitaets-gates-frontend.md)); Chart: Rendertests aller Secret-Provider; Repository: Prüfung auf eingecheckte lokale Agent-Einstellungen (RB-011). Zuordnung zu Workflows: [VT-004](../07-verteilung/VT-004-ci-cd-kette.md).
