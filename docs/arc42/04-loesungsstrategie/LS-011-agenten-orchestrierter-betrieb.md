---
id: LS-011
title: Agentengestützte Entwicklung mit Orchestrator und Wissensbasis
status: entwurf
typ: strategie
quellen:
  - AGENTS.md
  - docs/README.md
  - .agent-orchestrator/README.md
verknuepft: [RA-011, SH-007]
---

# Agentengestützte Entwicklung mit Orchestrator und Wissensbasis

Tickets werden teilweise von Agenten über einen Orchestrator bearbeitet ([RA-011](../02-randbedingungen/RA-011-agenten-orchestrierung-und-steward.md)). Das Wissen ist zweigeteilt: `docs/` (Vision, req42, arc42) ist verbindlich und nur mit menschlicher Freigabe änderbar, `wiki/` pflegen Agenten selbst, Widersprüche melden sie als Vorschlag in `wiki/proposals/`. Gemeinsame Regeln stehen in `AGENTS.md` (`CLAUDE.md` importiert sie); Hooks (Claude Code) und ein Plugin (opencode) erzwingen die Wiki-Pflege.
