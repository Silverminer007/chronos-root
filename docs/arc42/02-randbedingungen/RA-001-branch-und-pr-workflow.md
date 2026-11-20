---
id: RA-001
title: "Branch- und PR-Workflow"
status: entwurf
typ: randbedingung
quellen:
  - .agent-orchestrator/prompts/rules.extra.md
  - .github/CODEOWNERS
  - .github/workflows/backend-ci.yml
  - .github/workflows/frontend-ci.yml
verknuepft: [RB-005, RB-010, VT-004]
---

# RA-001: Branch- und PR-Workflow

Ergänzt RB-005 (nie direkt auf `main`) und RB-010 (Deployment-Regeln) um die technische Ausgestaltung:

- **PR-Konvention:** Titel `Resolve #<Ticket>: <Titel>`, Body beginnt mit `Closes #<Ticket>`; Pull Requests richten sich gegen `main` oder `develop` (`.agent-orchestrator/prompts/rules.extra.md`, Trigger der Workflows).
- **Prüfungen:** Die Workflows laufen pfadgefiltert (`backend/**`, `frontend/**`, `deployment/**`); Wirkung siehe [VT-004](../07-verteilung/VT-004-ci-cd-kette.md).
- **Freigabe von `docs/`:** `.github/CODEOWNERS` weist `/docs/` und `CODEOWNERS` dem Product Owner zu. Zusammen mit dem Branch-Schutz „Require review from Code Owners“ (laut Kommentar in `CODEOWNERS`; die Einstellung selbst liegt in GitHub und ist aus dem Repository nicht prüfbar **(?)**) erzwingt das die menschliche Freigabe.
