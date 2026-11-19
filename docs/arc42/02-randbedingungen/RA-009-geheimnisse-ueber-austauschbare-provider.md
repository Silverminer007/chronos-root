---
id: RA-009
title: "Geheimnisse über austauschbare Provider"
status: entwurf
typ: randbedingung
quellen:
  - kb/adr/ADR-2026-10-03-001-pluggable-secret-provider.md
  - deployment/README.md
verknuepft: []
---

# RA-009: Geheimnisse über austauschbare Provider

Das Helm-Chart bezieht Secrets über `secrets.provider` (Standard `bitwarden`, weitere Provider möglich). Keine Klartext-Secrets in Values oder Repository.
