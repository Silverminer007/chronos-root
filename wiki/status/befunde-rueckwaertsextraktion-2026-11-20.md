---
title: Befunde der Rückwärtsextraktion (Code vs. Erwartung)
type: status
sources:
  - docs/req42/12-risiken-annahmen/
  - docs/arc42/11-risiken/
  - frontend/server/
  - backend/src/main/java/
confidence: niedrig
last-verified: 2026-11-20
---

# Befunde (von Agenten aus dem Code abgeleitet, nicht manuell verifiziert)

- Frontend ruft Teilnahme-/Admin-Pfade auf (`participants/approve|reject|status|users|groups`, `/admin/statistics`, `/admin/user/`, Survey-Endpunkte), die das Java-Backend so nicht anbietet (`…/participation`, `/admin/stats`, `/admin/users`). Gegen Produktion prüfen.
- `ReminderEventCDIAdapter.sendReminder` ist leer: 30-Minuten-Erinnerung wirkungslos.
- Service Worker liest `new_attendee`, Backend sendet `new_participant`.
- Keine explizite Authentifizierungspflicht an Endpunkten; kein CSRF-Schutz im Proxy; Sentry nur konfiguriert, kein SDK.
- Rust-Reste außerhalb `docs/` (Helm-Templates, Workflows `backend-rust-test`/`contract-tests`, `load-tests/staging.js`, `.agent-orchestrator/project.env` mit `kb/`, `rustBackend.enabled` in `values-staging.yaml`) sind noch nicht bereinigt; Nutzer sagt: Rust nicht in Betrieb.
- Offen für Menschen: Branch-Schutz, Rotation LiteLLM-Schlüssel, Roadmap/Priorität.
