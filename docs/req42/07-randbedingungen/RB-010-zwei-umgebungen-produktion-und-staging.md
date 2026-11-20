---
id: RB-010
title: Zwei Umgebungen: Produktion und Staging
status: entwurf
typ: randbedingung
kategorie: organisatorisch
quellen: [deployment/values-prod.yaml, deployment/values-staging.yaml, .github/workflows/deployment-ci.yml, .github/workflows/backend-ci.yml]
verknuepft: []
---

# RB-010: Zwei Umgebungen: Produktion und Staging

Chronos läuft in Produktion (Stand `main`, Image-Tag `latest`) und Staging (Stand `develop`, Image-Tag `develop`). Der Deployment-Workflow rollt aus, wenn die Backend- oder Frontend-CI nach einem grünen Lauf auf `main` (Produktion) bzw. `develop` (Staging) ein Image mit neuer Versionsnummer gebaut hat und dann `deploy-production` bzw. `deploy-staging` auslöst; zusätzlich rollt ein Push auf `main` mit Änderungen unter `deployment/` Produktion aus. Die Umgebung wird per Helm aktualisiert ([BM-001](../09-betriebsmittel-personal/BM-001-laufzeitinfrastruktur.md)).
