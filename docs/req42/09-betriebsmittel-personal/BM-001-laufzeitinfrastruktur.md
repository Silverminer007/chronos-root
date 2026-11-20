---
id: BM-001
title: Laufzeitinfrastruktur
status: entwurf
typ: betriebsmittel
quellen: [deployment/values.yaml, deployment/values-prod.yaml, deployment/values-staging.yaml, deployment/templates/]
verknuepft: [SH-006]
---

# BM-001: Laufzeitinfrastruktur

| Betriebsmittel | Ausprägung |
|---|---|
| Kubernetes-Cluster | Namespaces `chronos-prod` und `chronos-staging` (Betrieb per Helm); Speicherklasse `hcloud-volumes` (Volumes der Hetzner Cloud) |
| Ingress | nginx mit automatischen Zertifikaten (Let's Encrypt über cert-manager); Hosts Produktion `chronos-live.de`, Staging `staging.chronos-live.de` |
| Frontend | Produktion 2 Replikate (Autoskalierung 1 bis 10), je 256 Mi / 250 m angefordert, 512 Mi / 500 m Limit; Staging 1 Replikat |
| Backend | Produktion 2 Replikate (Autoskalierung 1 bis 4), gleiche Größe wie Frontend; Staging 1 Replikat |
| Keycloak | Nicht Teil des Charts; erreichbar unter `auth.chronos-live.de` (Realm `chronos`), siehe [SC-001](../03-scope/SC-001-systemkontext-akteure-und-nachbarsysteme.md) |
| Datenbank | PostgreSQL 16, Speicher 10 Gi |
| Sicherung | S3-kompatibler Bucket (Zugangsdaten im Secret-Manager) |
| Monitoring | Prometheus, Grafana, Alarmregeln ([QA-007](../06-qualitaet/QA-007-beobachtbarkeit.md)) |
| Container-Registry | GitHub Container Registry (`ghcr.io`) |
| Secret-Verwaltung | Bitwarden Secrets Manager (Standard), alternativ Werte oder vorhandenes Secret |

Betriebskosten und Vertragslaufzeiten: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).
