---
id: RB-011
title: Keine Geheimnisse und lokale Agent-Einstellungen im Repository
status: entwurf
typ: randbedingung
kategorie: organisatorisch
quellen: [scripts/check-no-local-settings.sh, .github/workflows/repo-hygiene.yml, .github/workflows/frontend-ci.yml, .gitignore]
verknuepft: []
---

# RB-011: Keine Geheimnisse und lokale Agent-Einstellungen im Repository

Das Repository ist öffentlich. Geheimnisse (Schlüssel, Tokens, Passwörter) und `.claude/settings.local.json` gelangen nie hinein; die CI prüft das (Hygiene-Workflow bei jedem Push und Pull Request, Geheimnissuche im Frontend-Workflow). Die Historie wird bewusst nicht umgeschrieben; offener Befund: [arc42 R-001](../../arc42/11-risiken/README.md).
