---
id: QS-007
title: "Zugriffsschutz der Admin-API"
status: entwurf
typ: qualitaetsszenario
quellen:
  - backend/src/main/java/de/chronos_live/admin/presentation/
  - backend/src/test/java/de/chronos_live/chronos_date_api/presentation/AdminEndpointIntegrationTest.java
verknuepft: [QA-009, FA-007, AE-051, G-051, TD-016]
---

# QS-007: Zugriffsschutz der Admin-API

| | |
|---|---|
| **Reiz** | Anonym, ein normaler Benutzer oder ein Benutzer mit Realm-Rolle `ADMIN_API` ruft eine Route unter `/api/v2/admin/*` auf. |
| **Antwort** | Ohne gültiges Token 401, ohne Rolle `ADMIN_API` 403 (`@RolesAllowed` an allen sechs Admin-Resources), mit Rolle Zugriff. |
| **Messung** | Zugriff ohne Rolle auf alle sechs Resources (Appointment, Group, Friendship, User, Push, Statistics) ergibt 403. |
| **Nachweis** | `AdminEndpointIntegrationTest` prüft 403 für Nicht-Admins je Resource; der Erfolgsfall und 401 sind nicht abgedeckt, da OIDC im Test abgeschaltet ist **(?)**. |
