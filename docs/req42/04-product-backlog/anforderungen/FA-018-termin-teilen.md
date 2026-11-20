---
id: FA-018
title: Termin teilen
status: entwurf
typ: anforderung
quellen: [frontend/app/composables/useAppointmentShare.ts]
verknuepft: [F-019]
---

# FA-018: Termin teilen

Siehe das zugehörige Feature ([F-019](../features/F-019-termin-teilen.md)) für die Story.

## Akzeptanzkriterien
- Der Text enthält Name, Beschreibung (falls vorhanden), Start, Ende, Ort (falls vorhanden), die Zahl der Zusagen, Absagen und Teilnehmer, die Mindestteilnehmerzahl (falls gesetzt) und den Link zum Termin.
- Das Teilen nutzt das Teilen-Menü des Geräts; ohne dieses wird der Text in die Zwischenablage kopiert und per Hinweis bestätigt; Fehler werden angezeigt, ein Abbruch durch die Person nicht.
- Der Link führt zur Termindetailseite; sie ist nur für angemeldete Teilnehmer lesbar (wer den Link ohne Konto öffnet, wird zur Landingpage geleitet) ([FA-001](FA-001-termine-anlegen-aendern-verschieben-absagen-loeschen.md)).
