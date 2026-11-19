---
id: R-001
title: "Geleakter LiteLLM-Schlüssel in der Git-Historie"
status: entwurf
typ: risiko
quellen:
  - kb/constraints/C2026-10-20-002-leaked-litellm-key-in-history.md
verknuepft: []
---

# R-001: Geleakter LiteLLM-Schlüssel in der Git-Historie

`.claude/settings.local.json` war bis Ticket #142 versioniert und enthielt einen LiteLLM-Schlüssel; das Repository ist öffentlich. Der Schlüssel gilt als **kompromittiert** und muss vom Eigentümer rotiert werden. Die Historie wird bewusst nicht umgeschrieben. Maßnahme: Rotation **(?)** Status unbekannt. Vorbeugung: RA-008.
