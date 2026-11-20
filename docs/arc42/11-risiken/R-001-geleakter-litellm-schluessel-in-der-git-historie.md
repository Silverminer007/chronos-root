---
id: R-001
title: "Geleakter LiteLLM-Schlüssel in der Git-Historie"
status: entwurf
typ: risiko
quellen:
  - scripts/check-no-local-settings.sh
  - .github/workflows/repo-hygiene.yml
  - "Ticket #142 (geschlossen)"
verknuepft: [QS-014, RB-011]
---

# R-001: Geleakter LiteLLM-Schlüssel in der Git-Historie

**Risiko:** `.claude/settings.local.json` war bis Ticket #142 versioniert und enthielt einen LiteLLM-Schlüssel (Header `x-litellm-api-key`); das Repository ist öffentlich. Der Schlüssel gilt als kompromittiert, die Historie wird bewusst nicht umgeschrieben.

**Auswirkung:** Missbrauch des LiteLLM-Zugangs (Kosten, Datenabfluss), solange der Schlüssel gültig ist.

**Maßnahmen:** Rotation durch den Eigentümer, Status unbekannt **(?)**. Vorbeugung: Repo-Hygiene-Check ([QS-014](../10-qualitaet/QS-014-keine-geheimnisse-im-repository.md)).
