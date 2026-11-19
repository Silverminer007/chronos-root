---
id: R-007
title: "Agenten ändern Dokumentation ohne Prüfung"
status: entwurf
typ: risiko
quellen:
  - kb/status/steward.md
  - .agent-orchestrator/project.env
verknuepft: []
---

# R-007: Agenten ändern Dokumentation ohne Prüfung

`SAFE_PATHS_RE` stuft alle `*.md`-Dateien als sicher ein. `docs/` wurde zu `UNSAFE_PATHS_RE` ergänzt; ob UNSAFE im Orchestrator Vorrang hat, ist ungeprüft **(?)**. Zusätzlich braucht es Branch-Schutz "Require review from Code Owners" (siehe RA-011).
