---
id: RA-008
title: "Keine lokalen Agent-Einstellungen im Git"
status: entwurf
typ: randbedingung
quellen:
  - kb/constraints/C2026-10-20-001-no-local-agent-settings-in-git.md
  - .github/workflows/repo-hygiene.yml
verknuepft: []
---

# RA-008: Keine lokalen Agent-Einstellungen im Git

`.claude/settings.local.json` darf nie versioniert werden (enthält Zugangsdaten, Repository ist öffentlich). Durchsetzung: `.gitignore` und Workflow `repo-hygiene.yml` mit `scripts/check-no-local-settings.sh`.
