---
id: R-005
title: "Token-Audience wird nicht geprüft"
status: entwurf
typ: risiko
quellen:
  - kb/adr/ADR-2026-10-22-030-token-audience-handling.md
verknuepft: [RB-002]
---

# R-005: Token-Audience wird nicht geprüft

Beide Backends prüfen Signatur, Ablauf und Key-ID, aber weder Issuer noch standardmäßig die Audience (`AUTH_SERVER_AUDIENCE` optional). Bewusste Entscheidung zur Parität mit Quarkus; bei Mehrmandantenbetrieb zu überprüfen **(?)**.
