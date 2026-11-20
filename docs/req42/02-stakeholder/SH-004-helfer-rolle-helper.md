---
id: SH-004
title: Helfer (Rolle HELPER)
status: entwurf
typ: stakeholder
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/domain/UserRole.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/SettingsService.java]
verknuepft: []
---

# SH-004: Helfer (Rolle HELPER)

Helfer unterstützen bei Terminen. `HELPER` steht in der Rollenordnung zwischen `ATTENDANT` und `RESPONSIBLE` und erhält deshalb alle Benachrichtigungen, die auf `ATTENDANT` oder `HELPER` eingestellt sind ([M-007](../05-modelle/M-007-benachrichtigungsmatrix.md)).

**Einfluss:** Anwenderkreis ohne Entscheidungsrecht. Größe und Namen: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).

**Erwartung:** Informiert werden, wenn Termine geändert werden oder Rückmeldungen fehlen. Die Helferrechte entsprechen im Code denen von `ATTENDANT` ([M-006](../05-modelle/M-006-rollenmatrix.md)); ob sie sich unterscheiden sollen, ist offen ([RA-007](../12-risiken-annahmen/RA-007-berechtigung-zum-aendern-von-terminen-ist-weit-gefasst.md)).
