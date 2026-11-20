---
id: LS-009
title: Betrieb auf Kubernetes per Helm und GitHub Actions
status: entwurf
typ: strategie
quellen:
  - deployment
  - .github/workflows
verknuepft: [AE-014, RA-010, VT-001, VT-004]
---

# Betrieb auf Kubernetes per Helm und GitHub Actions

Ein Helm-Chart beschreibt Frontend, Backend, PostgreSQL, Backup und Monitoring. Zwei Umgebungen (Produktion aus `main`, Staging aus `develop`) teilen sich das Chart und unterscheiden sich nur in den Values ([VT-001](../07-verteilung/VT-001-umgebungen.md)). Secrets kommen über austauschbare Provider ([RA-009](../02-randbedingungen/RA-009-geheimnisse-ueber-austauschbare-provider.md)). GitHub Actions bauen die Images und lösen das Deployment aus ([VT-004](../07-verteilung/VT-004-ci-cd-kette.md)).
