---
id: RA-002
title: "Qualitäts-Gates Java-Backend"
status: entwurf
typ: randbedingung
quellen:
  - kb/constraints/001-code-quality-gates.md
  - CLAUDE.md
verknuepft: [QA-002]
---

# RA-002: Qualitäts-Gates Java-Backend

`./mvnw verify` führt Checkstyle (`checkstyle.xml`), SpotBugs (`spotbugs-exclude.xml`) und PMD (`pmd-ruleset.xml`) aus. Die CI (`backend-ci.yml`) erzwingt das bei jedem PR.
