---
id: RA-009
title: "Geheimnisse über austauschbare Provider"
status: entwurf
typ: randbedingung
quellen:
  - deployment/templates/secrets.yaml
  - deployment/templates/_helpers.tpl
  - deployment/values.yaml
verknuepft: [AE-014, VT-003, KT-012]
---

# RA-009: Geheimnisse über austauschbare Provider

Das Helm-Chart erzeugt oder erwartet alle Geheimnisse über genau einen Wert `secrets.provider` mit den Optionen `bitwarden` (Standard), `values` und `existing`; ein anderer Wert bricht das Rendern ab (`fail` in `secrets.yaml`). Die Anwendung liest Geheimnisse nur aus dem Kubernetes-Secret `chronos-secret` (Name über `secrets.existingSecretName` änderbar). Klartext-Secrets gehören nicht ins Repository. Ausprägung und Schlüssel: [VT-003](../07-verteilung/VT-003-secrets-provider.md); Entscheidung: [AE-014](../09-entscheidungen/AE-014-austauschbarer-secret-provider-im-helm-chart.md).
