---
id: FA-009
title: Terminliste paginiert abrufen
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/presentation/AppointmentsResource.java, backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/AppointmentRepository.java, frontend/app/pages/agenda.vue, frontend/app/stores/appointments.ts]
verknuepft: [F-002]
---

# FA-009: Terminliste paginiert abrufen

*Als Teilnehmer möchte ich meine Termine seitenweise abrufen und durchsuchen, damit die Agenda schnell lädt.*

## Akzeptanzkriterien
- Die Liste enthält genau die Termine, an denen die Person teilnimmt; gelöschte Termine (`DELETED`) fehlen, abgesagte (`CANCELLED`) erscheinen.
- Ohne Filter erscheinen Termine, die noch nicht beendet sind; optional begrenzen `start` und `end` den Zeitraum (ein Termin zählt, wenn er nach `start` endet und vor `end` beginnt).
- Die Suche findet Teilstücke in Name, Beschreibung und Ort ohne Beachtung der Groß-/Kleinschreibung.
- Die Sortierung erfolgt aufsteigend nach Startzeit.
- Die Antwort ist seitenweise (`page` ab 0, `size` Standard 10, Oberfläche 20) und nennt Seite, Größe und Gesamtzahl.
- Teilnehmer, Nachrichten und Gruppen sind auf Wunsch je Termin eingebettet und ersparen Folgeaufrufe; derselbe Umfang ist für einen einzelnen Termin abrufbar.
- Die Agenda zeigt Ladezustand, Fehlermeldung, den Hinweis „Keine Termine vorhanden“ und „Mehr laden“. Nach Rückkehr von einem Termin erscheint kein Eintrag doppelt (bekannter Fehler, Ticket #258 offen).
