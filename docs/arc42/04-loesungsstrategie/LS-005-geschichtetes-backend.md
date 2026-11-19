---
id: LS-005
title: Geschichtetes Backend
status: entwurf
typ: strategie
quellen:
  - kb/adr/ADR-2025-09-28-007-layered-backend.md
  - kb/architecture/ARCH-2025-09-28-003-layered-backend.md
verknuepft: [BB-004, BB-006]
---

# Geschichtetes Backend

Schichten presentation → application → infrastructure → domain; Abhängigkeiten nur nach unten. Ressourcen mappen HTTP, Services enthalten Fachlogik, Autorisierung und Events, Repositories kapseln Panache. Ergänzend `ports/` für Adapter (Identität, Benachrichtigung, Log, Reminder).
