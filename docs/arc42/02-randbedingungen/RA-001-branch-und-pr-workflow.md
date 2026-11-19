---
id: RA-001
title: "Branch- und PR-Workflow"
status: entwurf
typ: randbedingung
quellen:
  - CLAUDE.md
  - kb/constraints/003-deployment-rules.md
verknuepft: [RB-005]
---

# RA-001: Branch- und PR-Workflow

Nie direkt auf `main`. Jede Änderung läuft über einen neuen Branch und einen Draft-PR. PR-Titel `Resolve #<Ticket>: <Titel>`, Body beginnt mit `Closes #<Ticket>` (`.agent-orchestrator/prompts/rules.extra.md`). Branch-Schutz laut `003-deployment-rules.md`: mindestens eine Freigabe, grüne CI, kein Force-Push; `main` deployt nach Produktion, `develop` nach Staging.

Diese Randbedingung konkretisiert RB-005.
