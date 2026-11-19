---
id: BB-011
title: Agent-Orchestrator-Konfiguration (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - .agent-orchestrator/README.md
  - .agent-orchestrator/project.env
  - kb/status/steward.md
verknuepft: [LS-011]
---

# Agent-Orchestrator-Konfiguration (Ebene 1)

`project.env` (Bereiche, Pfade, Sidecar Postgres), `prompts/*.extra.md` (Projektregeln), `nightly.sh`, `image/Dockerfile`. Der Steward läuft als CronJob `steward` (alle 10 Minuten) im Namespace `chronos-agents`, gibt Folgetickets frei und nimmt gemergte ab (ab Ticket #299). Nie automatisch freigegeben werden Sicherheit/Rollen, Zugangsdaten/Kosten und Docker-Prüfbefehle.
