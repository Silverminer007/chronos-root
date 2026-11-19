---
id: RA-005
title: "Testgetriebene Entwicklung"
status: entwurf
typ: randbedingung
quellen:
  - kb/constraints/002-testing-requirements.md
verknuepft: [QA-002]
---

# RA-005: Testgetriebene Entwicklung

Neue Features werden testgetrieben entwickelt (Test zuerst); Java-Backend-Tests laufen mit Testcontainers gegen PostgreSQL und Fixtures aus `src/test/resources/db/testdata/`. Details: `kb/constraints/002-testing-requirements.md`.
