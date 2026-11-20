---
id: RA-011
title: "Agenten-Orchestrierung und Steward"
status: entwurf
typ: randbedingung
quellen:
  - .agent-orchestrator/project.env
  - .agent-orchestrator/README.md
  - .agent-orchestrator/nightly.sh
  - .agent-orchestrator/prompts
  - docs/README.md
  - AGENTS.md
verknuepft: [LS-011, SH-007]
---

# RA-011: Agenten-Orchestrierung und Steward

Entwicklungsaufgaben werden zum Teil von Agenten über eine externe Plattform (Repository `Silverminer007/agent-orchestrator`) bearbeitet. Das Projekt konfiguriert sie in `.agent-orchestrator/`; die Plattform selbst enthält nichts Projektspezifisches.

| Konfiguration | Inhalt (`project.env`) |
|---|---|
| Namespaces | `chronos-agents` (Agenten und Steward), `chronos-nightly` (Nightly-Lauf), `build` (Registry) |
| Bereiche | Ticket-Labels `area:java`, `area:frontend`, `area:deployment`, `area:docs` mit den Pfaden `backend/`, `frontend/`, `deployment/` und `docs/`; Folgetickets erhalten genau einen Bereich |
| Steward | läuft als CronJob alle zehn Minuten, nimmt nur Tickets ab Nr. 299 ab (`TICKET_FIRST`), gibt Folgetickets frei und schreibt Statusberichte |
| Nightly | `nightly.sh` baut Frontend (`npm ci`, Lint, Typecheck, Build) und Java (`./mvnw verify -DskipTests`); Quarkus-Tests brauchen Docker und laufen im Pod nicht, sie prüfen die GitHub Actions |
| Projektregeln | `prompts/*.extra.md` ergänzen die Standardprompts der Plattform |

Nie automatisch freigegeben werden Aufgaben zu Sicherheit, Keycloak, Rollen, Berechtigung und Datenschutz (`SEC_RE`), neue Zugangsdaten oder Kosten (`CRED_RE`, Label `needs-po`) sowie Prüfbefehle mit Docker oder Browser. Änderungen an `docs/`, `deployment/`, `.github/` und Migrationen gelten als unsicher (`UNSAFE_PATHS_RE`) und brauchen eine menschliche Freigabe; für `docs/` regeln das `docs/README.md` und `.github/CODEOWNERS`. Agenten lesen `docs/` und schreiben nur nach `wiki/` (`AGENTS.md`).

**(?)** `project.env` verweist noch auf das nicht mehr vorhandene Verzeichnis `kb/` (`KB_DIR`, `STATUS_DIR`, `AREA_PATHS` für `area:docs`) und ist nicht an den aktuellen Stand angepasst.
