---
id: R-007
title: "Agenten ändern Dokumentation ohne Prüfung"
status: entwurf
typ: risiko
quellen:
  - .agent-orchestrator/project.env
  - .github/CODEOWNERS
verknuepft: [RA-011, RB-005]
---

# R-007: Agenten ändern Dokumentation ohne Prüfung

**Risiko:** Der Agent-Orchestrator stuft Änderungen an `^(kb/|docs/|.*\.md$)` als sicher ein (`SAFE_PATHS_RE`), dieselben `docs/` stehen jedoch auch in `UNSAFE_PATHS_RE`. Welche Liste Vorrang hat, ist im Repository nicht prüfbar (Logik liegt im separaten Orchestrator-Repo) **(?)**. Zudem verweist `SAFE_PATHS_RE` noch auf das entfernte `kb/`.

**Auswirkung:** Verbindliche Dokumente könnten ohne menschliche Freigabe in `main` gelangen.

**Maßnahmen:** `.github/CODEOWNERS` verlangt Freigabe für `/docs/`; wirksam nur mit Branch-Schutz „Require review from Code Owners" auf `main` und `develop`, dessen Aktivierung nicht aus dem Repository ersichtlich ist **(?)**.
