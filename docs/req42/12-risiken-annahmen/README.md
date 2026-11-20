---
title: Risiken und Annahmen
status: entwurf
---

# Risiken und Annahmen

Produktrisiken und -annahmen. Wahrscheinlichkeit und Schadenshöhe sind Einschätzungen aus dem Code; mit **(?)** markierte Angaben bestätigt der Product Owner (Detail in der jeweiligen Datei). Alle Festlegungen, die nur ein Mensch treffen kann (Zielwerte, Zeitplan, Budget, Priorität usw.), stehen gebündelt in [RA-013](RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md); nur dort, hier in Kapitel 12 und in der [Roadmap](../11-roadmaps/README.md) darf **(?)** stehen. Technische Risiken und Schulden stehen in arc42 [Kapitel 11](../../arc42/11-risiken/README.md) (u. a. geleakter Schlüssel in der Historie, Keycloak als Single Point of Failure, Abhängigkeit vom Service Worker, nicht erzwungene Freigabe von `docs/`).

## Risiken

| ID | Risiko | Datei |
|---|---|---|
| RA-003 | Frontend und Backend nutzen unterschiedliche Schnittstellen | [RA-003-frontend-und-backend-schnittstellen-weichen-ab.md](RA-003-frontend-und-backend-schnittstellen-weichen-ab.md) |
| RA-004 | Datenschutzerklärung verspricht mehr, als die Anwendung leistet | [RA-004-datenschutzerklaerung-verspricht-mehr-als-die-anwendung-leistet.md](RA-004-datenschutzerklaerung-verspricht-mehr-als-die-anwendung-leistet.md) |
| RA-005 | Erinnerungen teilweise ohne Wirkung | [RA-005-erinnerungen-teilweise-ohne-wirkung.md](RA-005-erinnerungen-teilweise-ohne-wirkung.md) |
| RA-006 | Nutzerumfrage ohne Backend | [RA-006-nutzerumfrage-ohne-backend.md](RA-006-nutzerumfrage-ohne-backend.md) |
| RA-007 | Berechtigung zum Ändern von Terminen ist weit gefasst | [RA-007-berechtigung-zum-aendern-von-terminen-ist-weit-gefasst.md](RA-007-berechtigung-zum-aendern-von-terminen-ist-weit-gefasst.md) |
| RA-010 | Betrieb und Entwicklung hängen an einer Person | [RA-010-betrieb-und-entwicklung-haengen-an-einer-person.md](RA-010-betrieb-und-entwicklung-haengen-an-einer-person.md) |
| RA-012 | Standardeinstellungen unterdrücken die Einladungsbenachrichtigung | [RA-012-standardeinstellungen-unterdruecken-einladungsbenachrichtigung.md](RA-012-standardeinstellungen-unterdruecken-einladungsbenachrichtigung.md) |

## Annahmen

| ID | Annahme | Datei |
|---|---|---|
| RA-001 | Zielgruppe: Jugendorganisationen im deutschsprachigen Raum | [RA-001-zielgruppe-jugendorganisationen-im-deutschsprachigen-raum.md](RA-001-zielgruppe-jugendorganisationen-im-deutschsprachigen-raum.md) |
| RA-002 | Nutzer haben einen push-fähigen Browser | [RA-002-nutzer-haben-push-faehigen-browser.md](RA-002-nutzer-haben-push-faehigen-browser.md) |
| RA-008 | Wochentagsregeln gelten in UTC | [RA-008-wochentagsregeln-gelten-in-utc.md](RA-008-wochentagsregeln-gelten-in-utc.md) |
| RA-009 | Nutzungsumfang und Last sind unbekannt | [RA-009-nutzungsumfang-und-last-sind-unbekannt.md](RA-009-nutzungsumfang-und-last-sind-unbekannt.md) |
| RA-011 | Nutzer registrieren sich selbst bei Keycloak | [RA-011-nutzer-registrieren-sich-selbst-bei-keycloak.md](RA-011-nutzer-registrieren-sich-selbst-bei-keycloak.md) |
| RA-013 | Vom Product Owner festzulegende Werte und Entscheidungen | [RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md](RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md) |
