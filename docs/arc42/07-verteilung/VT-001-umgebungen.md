---
id: VT-001
title: Umgebungen
status: entwurf
typ: verteilung
quellen:
  - CLAUDE.md
  - deployment/values-prod.yaml
  - deployment/values-staging.yaml
  - deployment/values-local.yaml
  - deployment/values-parity.yaml
verknuepft: [LS-009, BB-009]
---

# Umgebungen

| Umgebung | Werte | Image-Tag | Auslöser |
|---|---|---|---|
| Produktion (`chronos-prod`, Host `chronos-live.de`) | `values-prod.yaml` | `latest` | Push auf `main` |
| Staging (`chronos-staging`) | `values-staging.yaml` | `develop` | Push auf `develop` |
| Lokal | `values-local.yaml` | lokal gebaut | manuell |
| Parity (`chronos-parity`, lokales k3s) | `values-local.yaml` + `values-parity.yaml` | `…:parity`, `pullPolicy: Never` | manuell, Contract-Lauf |
