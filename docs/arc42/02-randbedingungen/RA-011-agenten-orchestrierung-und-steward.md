---
id: RA-011
title: "Agenten-Orchestrierung und Steward"
status: entwurf
typ: randbedingung
quellen:
  - kb/status/steward.md
  - .agent-orchestrator/project.env
verknuepft: []
---

# RA-011: Agenten-Orchestrierung und Steward

Ein Steward (CronJob alle 10 Minuten, Namespace `chronos-agents`) nimmt Tickets ab Nr. 299 ab, gibt Folgetickets frei und schreibt Berichte nach `kb/status/`. Nie automatisch freigegeben werden Sicherheit/Keycloak/Rollen/Datenschutz, neue Zugangsdaten oder Kosten (`needs-po`) und Prüfbefehle mit Docker oder Browser. `docs/` ist nur mit menschlicher Freigabe änderbar (`UNSAFE_PATHS_RE`, CODEOWNERS).
