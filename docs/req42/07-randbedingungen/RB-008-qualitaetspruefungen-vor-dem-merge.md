---
id: RB-008
title: Qualitätsprüfungen vor dem Merge
status: entwurf
typ: randbedingung
kategorie: organisatorisch
quellen: [.github/workflows/backend-ci.yml, .github/workflows/frontend-ci.yml, .github/workflows/repo-hygiene.yml]
verknuepft: []
---

# RB-008: Qualitätsprüfungen vor dem Merge

Ein Merge setzt grüne Prüfungen voraus: im Backend Tests (`./mvnw test`) sowie Checkstyle, SpotBugs und PMD (`./mvnw verify -DskipTests`); im Frontend Lint, Typprüfung, Abhängigkeitsprüfung (`npm audit`, Schwere „high“), Geheimnissuche, Build und E2E-Tests; im Repository die Hygiene-Prüfung ([RB-011](RB-011-keine-geheimnisse-und-lokale-agent-einstellungen-im-repository.md)). Neue Funktionen werden testgetrieben entwickelt (Tests zuerst).

Bekannter Defekt: Der Java-Testbaum kompiliert laut offenem Ticket #256 nicht vollständig, sodass Teile der Backend-Tests in der CI nicht laufen.
