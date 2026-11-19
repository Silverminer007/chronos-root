---
id: KZ-007
title: Sicherheit und Tokenführung
status: entwurf
typ: konzept
quellen:
  - kb/constraints/C2025-09-28-002-oidc-required.md
  - kb/adr/ADR-2026-10-22-030-token-audience-handling.md
  - kb/constraints/C2026-10-20-001-no-local-agent-settings-in-git.md
verknuepft: [RB-002, RB-004, LS-002, LS-003]
---

# Sicherheit und Tokenführung

Tokens liegen nur in httpOnly-Cookies; der Nuxt-Server setzt den Bearer-Header. Backend validiert das JWT, Antwort 401 bei ungültigem, 403 bei fehlender Berechtigung. Die Audience des Access-Tokens wird standardmäßig nicht geprüft (ADR-030, nachdem Keycloak-Tokens mit `aud` abgelehnt wurden). Keine lokalen Agent-Einstellungen oder Schlüssel in Git; ein in der Historie geleakter LiteLLM-Schlüssel muss rotiert werden (C2026-10-20-002).
