---
id: QS-002
title: "Statische Qualität Java"
status: entwurf
typ: qualitaetsszenario
quellen:
  - .github/workflows/backend-ci.yml
  - backend/pmd-ruleset.xml
verknuepft: [QA-002]
---

# QS-002: Statische Qualität Java

**Szenario:** Ein PR ändert `backend/`. **Erwartung:** `./mvnw verify` (Checkstyle, SpotBugs, PMD) und `./mvnw test` sind grün, sonst wird nicht gemergt. **Nachweis:** Jobs `test` und `quality` in `backend-ci.yml`.
