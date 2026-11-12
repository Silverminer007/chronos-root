# Steward von chronos-root (Pod-Plattform des Agent Orchestrators)

Der Steward läuft alle 10 Minuten als CronJob `steward` im Namespace `chronos-agents` (Plattform: `deployment/k8s/steward/` im agent-orchestrator).
Ein Vorab-Skript ohne Modell entscheidet, ob es etwas zu tun gibt (Abnahme gemergter Tickets, Freigabe von Folgetickets, Rückfragen, Störungen, Bericht);
nur dann läuft `claude -p` mit einer klaren Aufgabe. Konfiguration: `.agent-orchestrator/project.env`.

## Regeln

- Tickets ab #299 werden abgenommen (`TICKET_FIRST`); ältere liefen über den früheren agentflow-Betrieb.
- Bereiche: `area:rust` (bekommt den Postgres-Sidecar, `TEST_DATABASE_URL`), `area:java`, `area:frontend`, `area:deployment`, `area:docs`. Ein Folgeticket bekommt genau einen Bereich.
- Nie automatisch freigeben: Sicherheit/Keycloak/Rollen/Datenschutz, neue Zugangsdaten oder Kosten (`needs-po`), Prüfbefehle mit Docker/Browser (`blocked`).
- Statusberichte liegen in `kb/status/` (`YYYY-MM-DD-HHMM.md`), offene Fragen an den Product Owner in `kb/status/po-notes.md` (`- [ ]`).
