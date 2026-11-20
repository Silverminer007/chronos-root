---
id: QA-007
title: Beobachtbarkeit
status: entwurf
typ: qualitaetsanforderung
quellen: [deployment/templates/prometheus-alerts.yaml, deployment/templates/backend-service-monitor.yaml, deployment/values.yaml]
verknuepft: []
---

# QA-007: Beobachtbarkeit

**Qualitätsmerkmal (ISO 25010):** Wartbarkeit / Betreibbarkeit (Analysierbarkeit)

## Szenario
Der Betrieb erkennt Störungen, bevor Nutzer sie melden: Das Backend liefert Prometheus-Metriken (`/metrics`) und Health-Status (Liveness und Readiness der Pods); Alarme lösen aus, wenn ein Pod 2 Minuten nicht läuft, die Datenbank 1 Minute nicht läuft, CPU über 80 % (5 Minuten) oder Speicher über 90 % (5 Minuten) liegt, der Datenbankspeicher über 85 % gefüllt ist, ein Pod mehr als dreimal pro Stunde neu startet oder ein Zertifikat in weniger als 7 Tagen abläuft.

## Messbar durch
Alarmregeln und Dienstüberwachung im Deployment. Der Alarmierungskanal ist im Repository nicht konfiguriert ([RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md)).
