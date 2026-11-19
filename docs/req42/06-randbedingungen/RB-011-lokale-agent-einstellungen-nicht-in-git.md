---
id: RB-011
title: Lokale Agent-Einstellungen nicht in Git
status: entwurf
typ: randbedingung
quellen: [kb/constraints/C2026-10-20-001-no-local-agent-settings-in-git.md, .github/workflows/repo-hygiene.yml]
verknuepft: []
---

# RB-011: Lokale Agent-Einstellungen nicht in Git

`.claude/settings.local.json` darf nie versioniert werden; der Workflow `repo-hygiene` prüft das bei jedem Push und PR.

## Herkunft
Bisher: C2026-10-20-001.
