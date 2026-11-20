---
id: SH-002
title: Gruppenleitung (Rolle RESPONSIBLE)
status: entwurf
typ: stakeholder
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java, frontend/app/components/AppointmentResponsibleActions.vue]
verknuepft: []
---

# SH-002: Gruppenleitung (Rolle RESPONSIBLE)

Verantwortliche legen Termine an, laden Personen und Gruppen ein, verschieben und sagen Termine ab und verwalten die Rollen der Teilnehmer. Wer einen Termin anlegt, ist automatisch dessen Verantwortliche/r. Rechte je Rolle: [M-006](../05-modelle/M-006-rollenmatrix.md).

**Einfluss:** Anwenderkreis ohne Entscheidungsrecht; wirkt über Rückmeldungen und die Nutzerumfrage ([FA-015](../04-product-backlog/anforderungen/FA-015-nutzerumfrage-beantworten.md)). Größe und Namen: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).

**Erwartung:** Schneller Überblick über Zusagen und Mindestteilnehmerzahl, wenig Aufwand für Einladungen, keine manuelle Nachfrage ([Z-001](../01-ziele/Z-001-termine-gemeinsam-planen-und-teilnahme-nachverfolgen.md), [Z-002](../01-ziele/Z-002-rueckmeldungen-ohne-nachfragen-erhalten.md), [Z-003](../01-ziele/Z-003-gruppen-und-freundeskreise-abbilden.md)).

**Hilft bei der Analyse:** kennt den Ablauf von Terminplanung und Rückmeldung (Quelle für [FA-001](../04-product-backlog/anforderungen/FA-001-termine-anlegen-aendern-verschieben-absagen-loeschen.md), [FA-011](../04-product-backlog/anforderungen/FA-011-automatische-erinnerungen.md)); ob `ATTENDANT` und `HELPER` Termine ändern sollen, ist offen ([RA-007](../12-risiken-annahmen/RA-007-berechtigung-zum-aendern-von-terminen-ist-weit-gefasst.md)).
