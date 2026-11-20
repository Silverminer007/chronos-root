---
id: TD-011
title: "CPU-Alarm-Schwelle über dem CPU-Limit"
status: entwurf
typ: technische-schuld
quellen:
  - deployment/templates/prometheus-alerts.yaml
  - deployment/values.yaml
  - deployment/values-prod.yaml
verknuepft: [QS-008, QA-007]
---

# TD-011: CPU-Alarm-Schwelle über dem CPU-Limit

`ChronosHighCPU` feuert bei `rate(container_cpu_usage_seconds_total{container="chronos"}[5m]) > 0.8` (Schwellenwert `alerts.rules.highCPU.threshold`), also bei mehr als 0,8 CPU-Kernen. Backend- und Frontend-Container (beide `chronos`) haben ein CPU-Limit von `500m` (Prod und Standardwerte); mehr als 0,5 Kerne sind nie messbar. Die Beschreibung des Alarms nennt dennoch „80 %“.

**Auswirkung:** Der Alarm für hohe CPU-Last kann nicht auslösen ([QS-008](../10-qualitaet/QS-008-betriebsueberwachung.md)).

**Maßnahme:** Schwelle relativ zum Limit ausdrücken (Rate geteilt durch `kube_pod_container_resource_limits{resource="cpu"}`) oder auf unter 0,5 setzen.
