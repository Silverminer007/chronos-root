---
id: QS-014
title: "Keine Geheimnisse im Repository"
status: entwurf
typ: qualitaetsszenario
quellen:
  - .github/workflows/repo-hygiene.yml
  - .github/workflows/frontend-ci.yml
  - scripts/check-no-local-settings.sh
  - scripts/check-no-local-settings.test.sh
verknuepft: [RB-011, R-001]
---

# QS-014: Keine Geheimnisse im Repository

| | |
|---|---|
| **Reiz** | Ein Commit versioniert `.claude/settings.local.json` oder enthält den Header `x-litellm-api-key`. |
| **Antwort** | Der Job `Repo Hygiene` (alle Pushes und PRs auf `main`/`develop`) prüft das Skript selbst (`check-no-local-settings.test.sh`) und schlägt über `check-no-local-settings.sh` fehl; bei Frontend-Änderungen scannt zusätzlich gitleaks die gesamte Historie. |
| **Messung** | 0 Treffer; der Job gibt Fundstellen, aber keine Werte aus. |
| **Nachweis** | Beide Workflows. Der Scan deckt Backend und `deployment/` nicht ab, und das Skript sucht nur diesen einen Marker **(?)**. |
