---
id: KT-007
title: Nachbarsystem Prometheus und Grafana
status: entwurf
typ: nachbarsystem
quellen:
  - deployment/templates/backend-service-monitor.yaml
  - deployment/templates/backend-service.yaml
  - deployment/values.yaml
  - backend/src/main/resources/application.properties
verknuepft: [KZ-009, VT-006, RA-010]
---

# Nachbarsystem Prometheus und Grafana

- **Rolle:** Metriken und Alarme über einen Prometheus-Operator im Cluster ([RA-010](../02-randbedingungen/RA-010-kubernetes-helm-deployment-mit-zwei-umgebungen.md)).
- **Schnittstelle:** Das Backend veröffentlicht Metriken und Health auf der Management-Schnittstelle (`quarkus.management.enabled=true`, Port 9000). Ein `ServiceMonitor` (Label `release: prometheus`) scrapt den Service-Port `metrics` (9000), Pfad `/metrics`, alle 30 s (Timeout 10 s). Health-Endpunkte `/q/health/live` und `/q/health/ready` auf Port 9000 dienen als Kubernetes-Probes.
- **Alarme:** `PrometheusRule` im Chart, Liste in [VT-006](../07-verteilung/VT-006-monitoring.md).
- **Grafana:** Das Chart enthält kein Dashboard für das Java-Backend; Metriken lassen sich nur über vorhandene Grafana-Instanzen des Clusters auswerten.
