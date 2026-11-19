---
id: RB-008
title: Qualitäts-Gates im Backend
status: entwurf
typ: randbedingung
quellen: [kb/constraints/001-code-quality-gates.md, CLAUDE.md]
verknuepft: []
---

# RB-008: Qualitäts-Gates im Backend

`./mvnw verify` führt Checkstyle, SpotBugs und PMD aus; die CI erzwingt sie bei jedem PR.

## Herkunft
Bisher: 001-code-quality-gates.
