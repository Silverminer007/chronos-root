---
id: RB-010
title: Deployment-Regeln für main und develop
status: entwurf
typ: randbedingung
quellen: [kb/constraints/003-deployment-rules.md, CLAUDE.md]
verknuepft: []
---

# RB-010: Deployment-Regeln für main und develop

Push auf `main` deployt `latest` in Produktion, `develop` deployt in Staging; Merge nur mit grüner CI und Review. **(?)** Teile der Regeln sind in `kb/constraints/003-deployment-rules.md` älter als die Praxis (z. B. Branchnamen).

## Herkunft
Bisher: 003-deployment-rules.
