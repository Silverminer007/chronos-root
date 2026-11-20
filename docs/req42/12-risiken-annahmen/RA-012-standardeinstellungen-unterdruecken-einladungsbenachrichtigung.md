---
id: RA-012
title: Standardeinstellungen unterdrücken die Einladungsbenachrichtigung
status: entwurf
typ: risiko
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/SettingsService.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/WebPushService.java]
verknuepft: [Z-002, FA-002, FA-012, M-007]
---

# RA-012: Standardeinstellungen unterdrücken die Einladungsbenachrichtigung

**Risiko:** Die Benachrichtigung „Person eingeladen“ geht an die Teilnehmer, deren Einstellung `appointment_participant_added` ihre Rolle einschließt; Standard ist `RESPONSIBLE`. Eine neu eingeladene Person mit Rolle `GUEST`, `ATTENDANT` oder `HELPER` erfährt von ihrer Einladung deshalb mit den Standardeinstellungen nichts, solange sie die Einstellung nicht ändert. Das widerspricht dem Ziel, Einladungen aktiv zuzustellen ([Z-002](../01-ziele/Z-002-rueckmeldungen-ohne-nachfragen-erhalten.md)).

**Wahrscheinlichkeit / Schaden:** hoch (Standardfall) / mittel. **Maßnahme:** Einladung an die eingeladene Person unabhängig von der Einstellung senden oder Standard ändern.
