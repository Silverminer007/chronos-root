---
id: KT-012
title: Nachbarsystem Bitwarden Secrets Manager
status: entwurf
typ: nachbarsystem
quellen:
  - deployment/templates/secrets.yaml
  - deployment/templates/_helpers.tpl
  - deployment/values-prod.yaml
verknuepft: [RA-009, VT-003]
---

# Nachbarsystem Bitwarden Secrets Manager

Bei `secrets.provider: bitwarden` (Standard, Produktion und Staging) legt das Chart eine `BitwardenSecret`-Ressource an. Der Bitwarden-Operator im Cluster holt damit die Werte aus der Organisation (`secrets.organisationId`) und schreibt sie ins Secret `chronos-secret`; im Chart stehen nur Secret-IDs. Der Operator authentifiziert sich mit dem Secret `bw-auth-token` (Schlüssel `token`). Schlüsselliste und Alternativen: [VT-003](../07-verteilung/VT-003-secrets-provider.md).
