---
id: RA-007
title: Berechtigung zum Ändern von Terminen ist weit gefasst
status: entwurf
typ: risiko
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java]
verknuepft: [SH-002, FA-001, M-006]
---

# RA-007: Berechtigung zum Ändern von Terminen ist weit gefasst

**Risiko:** Ändern und Verschieben eines Termins ist ab Rolle `ATTENDANT` erlaubt, nicht nur für Verantwortliche; Absagen und Löschen dagegen nur für Verantwortliche. Teilnehmer könnten Termine ungewollt verschieben, und die Oberfläche zeigt „Termin bearbeiten“ allen Teilnehmern. Die Rolle `HELPER` hat dieselben Rechte wie `ATTENDANT` ([M-006](../05-modelle/M-006-rollenmatrix.md)). **(?)** Unklar, ob das gewollt ist und ob Helfer eigene Rechte erhalten sollen.

**Wahrscheinlichkeit / Schaden:** mittel (jeder `ATTENDANT` kann es tun) / mittel. **Maßnahme:** Product Owner entscheidet über die Mindestrolle; Test ergänzen.
