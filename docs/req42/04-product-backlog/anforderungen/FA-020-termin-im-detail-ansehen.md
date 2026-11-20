---
id: FA-020
title: Termin im Detail ansehen
status: entwurf
typ: anforderung
quellen: [frontend/app/pages/appointment/[appointmentId].vue, frontend/app/components/AppointmentDetailsCard.vue, frontend/app/components/ParticipantsStatusCard.vue, frontend/app/components/OwnParticipationStatusCard.vue, frontend/app/components/GroupParticipantsCard.vue, frontend/app/components/AppointmentHeader.vue]
verknuepft: [F-002, F-005]
---

# FA-020: Termin im Detail ansehen

*Als Teilnehmer möchte ich auf einer Seite Eckdaten, meine Teilnahme und den Stand der Rückmeldungen sehen, damit ich entscheide und den Überblick behalte.*

## Akzeptanzkriterien
- Die Detailseite `/appointment/<id>` zeigt Name, Datum, Uhrzeit von–bis, Ort und Beschreibung (die beiden Letzten nur, wenn vorhanden) sowie „Mindestens n Teilnehmer erforderlich“. Bei einem Termin im Status `CANCELLED`, `NOT_ENOUGH_ATTENDEES` oder `DELETED` erscheint ein Hinweis mit dem Zustand; bei `PLANNED` nicht.
- Die eigene Teilnahme zeigt „Zusagen“ und „Absagen“; der gerade gewählte Zustand ist hervorgehoben und deaktiviert ([FA-003](FA-003-zu-und-absage-mit-mindestteilnehmerzahl.md)). Solange der Status `PENDING` ist, erscheint der Hinweis „Bitte bestätige deine Teilnahme“.
- Die Teilnehmerliste nennt „x / y zugesagt“ mit Fortschrittsbalken und je Person Name, Rolle (Organisator, Teilnehmer, Helfer, Gast) und Rückmeldung (Zugesagt, Abgesagt, Ausstehend). Sie ist nach Rolle, dann Rückmeldung (zugesagt vor abgesagt vor ausstehend), dann Name sortiert. Teilnahmen, die über eine Gruppe entstanden sind, nennen den Gruppennamen ([FA-002](FA-002-personen-und-gruppen-einladen.md)).
- Die Gruppenliste zeigt die eingeladenen Gruppen oder „Keine Gruppen eingeladen“. Teilnehmer hinzufügen, Gruppen hinzufügen und entfernen sowie Rollen ändern ([FA-002](FA-002-personen-und-gruppen-einladen.md), [FA-010](FA-010-rolle-eines-teilnehmers-aendern.md)) bietet die Seite nur `RESPONSIBLE` an.
- Die Seite bettet die Nachrichten des Termins ein ([FA-005](FA-005-nachrichten-zu-einem-termin.md)) und bietet „Link teilen“ ([FA-018](FA-018-termin-teilen.md)) und „Termin bearbeiten“ ([FA-001](FA-001-termine-anlegen-aendern-verschieben-absagen-loeschen.md)).
- Während des Ladens erscheint „Termin wird geladen...“; schlägt das Laden fehl (z. B. keine Teilnahme, `403`), erscheint „Termin nicht gefunden“ mit „Zurück zur Übersicht“.
