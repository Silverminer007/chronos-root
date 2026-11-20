---
id: RB-005
title: Änderungen an main nur per Pull Request
status: entwurf
typ: randbedingung
kategorie: organisatorisch
quellen: [.github/CODEOWNERS, AGENTS.md, docs/README.md]
verknuepft: []
---

# RB-005: Änderungen an main nur per Pull Request

Änderungen gelangen nur über Branch und Pull Request nach `main`, nie durch direktes Committen. Änderungen unter `docs/` gelten erst nach menschlicher Freigabe durch die Code-Owner. Technisch erzwungen ist nur ein Teil: Ein GitHub-Ruleset auf `main` verlangt einen Pull Request (ohne Mindestzahl an Freigaben, ohne Pflicht zur Prüfung durch Code-Owner), lineare Historie und verbietet Löschen und Force-Push. Für `develop` gibt es keinen Schutz; die Freigabe von `docs/` durch die Code-Owner ist Konvention ([TS-001](../10-teamstruktur/TS-001-rollen-und-zusammenarbeit.md)). Ob ein strengerer Schutz eingerichtet wird: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).
