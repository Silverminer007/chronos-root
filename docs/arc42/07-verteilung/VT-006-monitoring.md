---
id: VT-006
title: Monitoring und Alarme
status: entwurf
typ: verteilung
quellen:
  - deployment/templates/prometheus-alerts.yaml
  - DEPLOYMENT.md
verknuepft: [KT-007, KZ-009]
---

# Monitoring und Alarme

ServiceMonitor für beide Backends, PrometheusRule mit zwölf Alarmen (Pod down, CPU/Speicher, PostgreSQL down/Disk, Neustarts, TLS-Ablauf, Rust-Latenz). `DEPLOYMENT.md` beschreibt eine wellenweise Canary-Einführung des Rust-Backends (u. a. Fehlerrate < 1 %, p95 < 1000 ms); ob sie praktiziert wird, ist **(?)**. Lasttests: `load-tests/staging.js`.
