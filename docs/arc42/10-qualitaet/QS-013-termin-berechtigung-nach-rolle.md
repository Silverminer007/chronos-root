---
id: QS-013
title: "Termin-Berechtigung nach Rolle"
status: entwurf
typ: qualitaetsszenario
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java
  - backend/src/test/java/de/chronos_live/chronos_date_api/application/AuthorizationServiceTest.java
  - backend/src/test/java/de/chronos_live/chronos_date_api/presentation/ErrorHandlingIntegrationTest.java
verknuepft: [QA-009, AE-051, KZ-002, G-004]
---

# QS-013: Termin-Berechtigung nach Rolle

| | |
|---|---|
| **Reiz** | Ein Benutzer ohne Rolle am Termin (`NONE`) oder ohne `RESPONSIBLE` versucht zu lesen, zu löschen, abzusagen oder Teilnehmer/Gruppen zu ändern. |
| **Antwort** | `AuthorizationService` wirft `ForbiddenException`; die API antwortet 403 mit `ErrorResponse`. |
| **Messung** | Keine der in [AE-051](../09-entscheidungen/AE-051-berechtigung-ueber-teilnehmerrolle-am-termin.md) tabellierten Aktionen gelingt ohne die geforderte Rolle (Admin-Anfragen ausgenommen). |
| **Nachweis** | `AuthorizationServiceTest` (Unit) und `ErrorHandlingIntegrationTest` (403 bei Löschen durch Nicht-Verantwortliche und Hinzufügen durch Unberechtigte). |
