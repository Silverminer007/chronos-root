---
id: QA-009
title: Datenschutz und Geheimnisschutz
status: entwurf
typ: qualitaetsanforderung
quellen: [kb/constraints/C2025-09-28-002-oidc-required.md, kb/constraints/C2026-10-20-001-no-local-agent-settings-in-git.md]
verknuepft: []
---

# QA-009: Datenschutz und Geheimnisschutz

**Qualitätsmerkmal:** Sicherheit

## Szenario
Tokens liegen nur in httpOnly-Cookies, Geheimnisse und lokale Agent-Einstellungen gelangen nicht ins Repository. **(?)** Weitere Datenschutzanforderungen (Löschkonzept, Auftragsverarbeitung) klären.

## Messbar durch
CI-Prüfung `repo-hygiene`.
