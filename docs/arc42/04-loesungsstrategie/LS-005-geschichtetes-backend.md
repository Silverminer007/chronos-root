---
id: LS-005
title: Geschichtetes Backend mit Ports
status: entwurf
typ: strategie
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api
verknuepft: [AE-010, BB-005, BB-006, BB-008]
---

# Geschichtetes Backend mit Ports

Vier Schichten mit Abhängigkeiten nur nach unten: `presentation` (JAX-RS-Ressourcen) → `application` (Services mit Fachlogik, Autorisierung und Ereignissen) → `infrastructure` (Panache-Repositories, Adapter) → `domain` (Entitäten). Ressourcen bilden nur HTTP ab. Zugriffe auf externe Systeme (Identität, Benachrichtigung, Benachrichtigungsprotokoll, Erinnerungsereignis) laufen über Ports in `application/ports`, damit Fachlogik keine Keycloak- oder Push-Typen kennt. Bausteine: [BB-006](../05-bausteine/BB-006-java-schichten.md), [BB-008](../05-bausteine/BB-008-events-und-ports.md); Begründung: [AE-010](../09-entscheidungen/AE-010-geschichtetes-backend-mit-ports.md).
