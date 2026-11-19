---
id: EIN-004
title: "Stakeholder"
status: entwurf
typ: einfuehrung
quellen:
  - docs/req42/02-stakeholder/README.md
  - kb/status/steward.md
verknuepft: [Z-001]
---

# EIN-004: Stakeholder

Stakeholder sind in `docs/req42/` geführt (je Stakeholder eine Datei). Aus Architektursicht relevant:

| Rolle | Erwartung an die Architektur |
|---|---|
| Product Owner | Entscheidungen zu Zielbackend und Funktionsumfang, Freigabe von `docs/` |
| Entwickler und Agenten | Klare Schichten, Tests und Gates, nachvollziehbare Entscheidungen |
| Betrieb | Helm-Chart, Monitoring, Backups |
| Benutzer (Gruppenleitung, Teilnehmer) | Zuverlässige Termine und Benachrichtigungen |
