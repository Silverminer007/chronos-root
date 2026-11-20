---
id: R-index
title: "11 Risiken und technische Schulden"
status: entwurf
typ: index
quellen:
  - backend/
  - frontend/
  - deployment/
verknuepft: [RA-003, RA-005]
---

# 11 Risiken und technische Schulden

Risiken (`R-nnn`) sind mögliche künftige Probleme, technische Schulden (`TD-nnn`) bereits vorhandene Mängel im Ist-Zustand. Jeder Eintrag nennt Beleg, Auswirkung und, wo bekannt, Maßnahmen. Offene Tickets: `gh issue list --state open`. Die Priorität (hoch = Kernfunktion oder Sicherheit betroffen, mittel = Teilfunktion oder Betrieb, niedrig = Komfort oder geringer Schaden) ist eine Einschätzung aus dem Code, vom Product Owner zu bestätigen; die Tabelle ist danach geordnet. Fachliche Annahmen und Risiken stehen in req42 ([Kapitel 12](../../req42/12-risiken-annahmen/README.md)).

| Priorität | ID | Titel | Art |
|---|---|---|---|
| hoch | [TD-014](TD-014-frontend-ruft-pfade-ohne-backend-gegenstueck.md) | Frontend ruft Pfade ohne Backend-Gegenstück | Schnittstelle |
| hoch | [R-008](R-008-proxy-ohne-csrf-schutz.md) | Proxy ohne CSRF-Schutz | Sicherheit |
| hoch | [R-001](R-001-geleakter-litellm-schluessel-in-der-git-historie.md) | Geleakter LiteLLM-Schlüssel in der Git-Historie | Sicherheit |
| hoch | [R-006](R-006-datenbank-ist-einziger-zustandsspeicher.md) | Datenbank ist einziger Zustandsspeicher | Verfügbarkeit, Daten |
| mittel | [TD-008](TD-008-30-minuten-erinnerung-ohne-wirkung.md) | 30-Minuten-Erinnerung ohne Wirkung | Fachlogik |
| mittel | [TD-013](TD-013-doppelte-erinnerung-bei-termin-auf-cron-tick.md) | Doppelte Erinnerung bei Terminen auf dem Cron-Tick | Fachlogik |
| mittel | [TD-012](TD-012-minimalattendees-null-fuehrt-zu-npe.md) | Fehlende Mindestteilnehmerzahl führt zu NullPointerException | Fachlogik |
| mittel | [R-010](R-010-asynchrone-beobachter-der-zu-absage-laufen-vor-dem-commit.md) | Asynchrone Beobachter der Zu-/Absage laufen vor dem Commit | Konsistenz |
| mittel | [TD-015](TD-015-service-worker-liest-new-attendee-backend-sendet-new-participant.md) | Service Worker liest new_attendee, Backend sendet new_participant | Schnittstelle |
| mittel | [TD-010](TD-010-rueckmelde-bestaetigung-ohne-wirkung.md) | Rückmelde-Bestätigung ohne Wirkung | Fachlogik |
| mittel | [R-004](R-004-keycloak-als-single-point-of-failure.md) | Keycloak als Single Point of Failure | Verfügbarkeit |
| mittel | [R-005](R-005-token-audience-wird-nicht-geprueft.md) | Token-Audience wird nicht geprüft | Sicherheit |
| mittel | [R-003](R-003-abhaengigkeit-vom-service-worker-fuer-token-refresh.md) | Abhängigkeit vom Service Worker für Token-Refresh | Betrieb, Frontend |
| mittel | [R-007](R-007-agenten-aendern-dokumentation-ohne-pruefung.md) | Agenten ändern Dokumentation ohne Prüfung | Prozess |
| mittel | [TD-011](TD-011-cpu-alarm-schwelle-ueber-dem-cpu-limit.md) | CPU-Alarm-Schwelle über dem CPU-Limit | Betrieb |
| mittel | [TD-004](TD-004-frontend-unit-tests-fehlen.md) | Frontend-Unit-Tests fehlen | Tests |
| niedrig | [TD-016](TD-016-fehlendes-token-fuehrt-zu-500-statt-401.md) | Fehlendes Token führt zu 500 statt 401 | Fehlerbehandlung |
| niedrig | [TD-017](TD-017-sentry-nur-konfiguriert-kein-sdk.md) | Sentry nur konfiguriert, kein SDK | Betrieb |
| niedrig | [R-009](R-009-profil-upsert-bei-jeder-anfrage.md) | Profil-Upsert bei jeder Anfrage | Performance |
| niedrig | [TD-009](TD-009-englische-fehlermeldungen-im-backend.md) | Englische Fehlermeldungen im Backend | Bedienbarkeit |

IDs sind stabil; entfallene IDs (R-002, TD-001 bis TD-003, TD-005 bis TD-007) werden nicht wiederverwendet.
