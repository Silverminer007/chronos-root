---
id: QA-004
title: Sicherheit: kein sichtbarer Token-Ablauf
status: entwurf
typ: qualitaetsanforderung
quellen: [CLAUDE.md, kb/adr/ADR-2025-09-28-005-service-worker.md]
verknuepft: []
---

# QA-004: Sicherheit: kein sichtbarer Token-Ablauf

**Qualitätsmerkmal:** Sicherheit

## Szenario
Der Ablauf des Access-Tokens führt nicht zu sichtbaren 401-Fehlern; der Service Worker erneuert Tokens proaktiv (unter 5 s Restlaufzeit, geplant 30 s vor Ablauf).

## Messbar durch
Manuelle Prüfung; **(?)** automatisierter Test fehlt.
