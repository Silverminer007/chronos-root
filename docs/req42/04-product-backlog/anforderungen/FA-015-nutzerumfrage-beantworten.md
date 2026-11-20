---
id: FA-015
title: Nutzerumfrage beantworten
status: entwurf
typ: anforderung
quellen: [frontend/app/pages/survey/[surveyId].vue, frontend/public/push-sw.js, frontend/app/pages/admin/index.vue]
verknuepft: [F-016]
---

# FA-015: Nutzerumfrage beantworten

*Als Eingeladene/r möchte ich einmalig an einer Umfrage teilnehmen, damit mein Feedback berücksichtigt wird.*

## Akzeptanzkriterien
- Die Umfrage hat fünf optionale Fragen: Einzelauswahl mit Freitext bei „Etwas anderes“, Freitext (30 Wörter), Mehrfachauswahl mit exklusivem „Keines davon“, Mehrfachauswahl in zwei Gruppen (Teilnehmer, Organisator) und Freitext (20 Wörter). Der Wortzähler färbt sich über dem Limit rot, das Limit wird nicht erzwungen.
- Absenden übermittelt nur beantwortete Fragen, zeigt „Danke!“ und führt nach zwei Sekunden zur Agenda; ein Fehler zeigt eine Meldung und erlaubt erneutes Absenden.
- Wer bereits teilgenommen hat (Eligibility-Prüfung oder Fehler `400` beim Absenden), sieht „Danke, du hast bereits geantwortet!“ mit Link zur Übersicht.
- Der Aufruf aus einer Push-Einladung (`?from=push`) überspringt die Eligibility-Prüfung; Administratoren versenden die Einladung als Push-Benachrichtigung („Umfrage“, [M-007](../../05-modelle/M-007-benachrichtigungsmatrix.md)).
- Die Schnittstellen `survey/{id}/eligible`, `survey/{id}/responses` und `admin/survey/{id}/notify` fehlen im Backend; die Kriterien sind aus der Oberfläche abgeleitet ([RA-006](../../12-risiken-annahmen/RA-006-nutzerumfrage-ohne-backend.md)).
