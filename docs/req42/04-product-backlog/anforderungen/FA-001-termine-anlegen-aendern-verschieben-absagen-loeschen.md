---
id: FA-001
title: Termine anlegen, ändern, verschieben, absagen, löschen
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentService.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java, frontend/app/components/CreateAppointmentStepper.vue]
verknuepft: [F-001]
---

# FA-001: Termine anlegen, ändern, verschieben, absagen, löschen

*Als Verantwortliche/r möchte ich Termine anlegen, ändern, verschieben, absagen und löschen, damit der Plan stets aktuell ist.*

## Akzeptanzkriterien
- Anlegen: Name (nicht leer), Start und Ende sind Pflicht; Beschreibung, Ort und Mindestteilnehmerzahl sind optional. Ein Ende vor dem Start oder eine negative Mindestteilnehmerzahl wird mit einem Validierungsfehler abgelehnt. Die Oberfläche setzt ohne gewählte Mindestanzahl den Wert 0.
- Ein neuer Termin hat den Status `PLANNED`; die anlegende Person ist Teilnehmerin mit Rolle `RESPONSIBLE` und Teilnahmestatus `PENDING`.
- Ändern von Name, Beschreibung, Ort, Zeitraum und Mindestteilnehmerzahl darf, wer mindestens Rolle `ATTENDANT` hat; Personen mit Rolle `GUEST` oder ohne Teilnahme erhalten `403`. Dass auch `ATTENDANT` und `HELPER` ändern dürfen, ist so umgesetzt, aber fachlich nicht bestätigt ([RA-007](../../12-risiken-annahmen/RA-007-berechtigung-zum-aendern-von-terminen-ist-weit-gefasst.md)).
- Verschieben ist eine Änderung von Start oder Ende; sie löst eine Systemnachricht ([FA-008](FA-008-terminaenderungen-nachvollziehbar-mitteilen.md)) und eine Push-Benachrichtigung ([FA-004](FA-004-push-benachrichtigungen.md)) aus. Eine Änderung ohne neuen Zeitraum löst beides nicht aus.
- Nach jeder Änderung wird die Mindestteilnehmerzahl neu bewertet ([M-003](../../05-modelle/M-003-statusautomat-des-termins.md)).
- Absagen und Löschen darf nur eine Person mit Rolle `RESPONSIBLE`, andere erhalten `403`.
- Absagen setzt den Status `CANCELLED`, erzeugt die Systemnachricht „<Name> hat diesen Termin abgesagt. Er wird nicht stattfinden!“ und benachrichtigt die übrigen Teilnehmer; der Termin bleibt sichtbar.
- Löschen setzt den Status `DELETED` (Soft-Delete); der Termin ist danach weder in Listen noch per Abruf auffindbar (`404`). Die Oberfläche bietet nur „Termin absagen“, kein Löschen (offene Designentscheidung, Ticket #380: [RA-013](../../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md)).
- Wer keine Teilnahmerolle am Termin hat, kann ihn nicht lesen (`403`).

## Akzeptanzkriterien: Oberfläche
- Der Anlege-Dialog führt in sechs Schritten durch Name, Datum und Zeit, Dauer, Beschreibung, Ort und Teilnehmer; „Weiter“ ist nur aktiv, wenn der Name nicht leer, ein Start gewählt und die Dauer größer als 0 ist; Beschreibung, Ort und Teilnehmer sind überspringbar.
- Vorbelegt sind der Start auf die nächste volle Stunde und die Dauer 60 Minuten; Schnellwahl „Heute“, „Morgen“, „Übermorgen“ für den Tag und Dauerchips von 30 Minuten bis 14 Tage sowie eine freie Dauer in Minuten, Stunden oder Tagen. Das Ende wird aus Start und Dauer berechnet.
- Die Mindestteilnehmerzahl wird nur gesendet, wenn sie eingeschaltet ist (Vorschlag 5), sonst 0.
- Im Schritt „Teilnehmer“ werden Freunde und Gruppen mit Rolle gewählt; sie werden nach dem Anlegen einzeln eingeladen. Schlägt eine Einladung fehl, bleibt der Termin bestehen und ein Hinweis nennt die Zahl der fehlgeschlagenen Einladungen. Danach öffnet sich der Termin.
- „Termin bearbeiten“ (Bottom-Sheet mit denselben Feldern, Speichern nur bei Änderung) ist allen Teilnehmern sichtbar; ein Speichern ohne Berechtigung (`GUEST`) endet mit der Fehlermeldung „Änderungen konnten nicht gespeichert werden“.
- „Termin absagen“ erscheint nur für `RESPONSIBLE` in den Organisator-Aktionen, verlangt eine Bestätigung und ist bei einem abgesagten Termin deaktiviert.
