---
id: QS-008
title: "Betriebsüberwachung"
status: entwurf
typ: qualitaetsszenario
quellen:
  - deployment/templates/prometheus-alerts.yaml
  - kb/adr/ADR-2026-10-20-020-prometheus-metrics-health-parity.md
verknuepft: []
---

# QS-008: Betriebsüberwachung

**Szenario:** Pod fällt aus, hohe CPU/Speicher, Datenbank down oder Zertifikat läuft ab. **Erwartung:** Prometheus-Alarme (u. a. `ChronosPodDown`, `PostgreSQLDown`, `SSLCertExpiringSoon`, `RustBackendHighErrorRate`) feuern. Beide Backends liefern Metriken und `/q/health` kompatibel.
