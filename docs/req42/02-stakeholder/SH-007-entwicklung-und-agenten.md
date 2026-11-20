---
id: SH-007
title: Entwicklung und Agenten
status: entwurf
typ: stakeholder
quellen: [AGENTS.md, .agent-orchestrator/README.md, .agent-orchestrator/project.env]
verknuepft: []
---

# SH-007: Entwicklung und Agenten

Entwicklerinnen, Entwickler und KI-Agenten (Agent Orchestrator, Claude Code, opencode) setzen Tickets um und pflegen Code, Wiki und Dokumentationsvorschläge. Rollen und Ablauf: [TS-001](../10-teamstruktur/TS-001-rollen-und-zusammenarbeit.md).

**Einfluss:** mittel; setzt um, ändert aber `docs/` nie selbst und merged nur nach menschlicher Freigabe ([RB-005](../07-randbedingungen/RB-005-aenderungen-an-main-nur-per-pull-request.md)). Personen: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).

**Erwartung:** Eindeutige Regeln ([AGENTS.md](../../../AGENTS.md)), grüne CI ([RB-008](../07-randbedingungen/RB-008-qualitaetspruefungen-vor-dem-merge.md)), verlässliche Dokumentation.

**Hilft bei der Analyse:** schätzt Aufwand und Machbarkeit; der Code ist die Quelle dieses req42. **Hindert:** unvollständige oder widersprüchliche Anforderungen, z. B. [RA-003](../12-risiken-annahmen/RA-003-frontend-und-backend-schnittstellen-weichen-ab.md).
