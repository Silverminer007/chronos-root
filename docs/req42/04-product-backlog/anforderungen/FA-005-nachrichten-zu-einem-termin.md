---
id: FA-005
title: Nachrichten zu einem Termin
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/MessageService.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/WebPushService.java]
verknuepft: [F-009]
---

# FA-005: Nachrichten zu einem Termin

*Als Teilnehmer möchte ich zu einem Termin Nachrichten austauschen, damit Absprachen beim Termin bleiben.*

## Akzeptanzkriterien
- Lesen und Schreiben darf, wer eine Teilnahmerolle (nicht `NONE`) am Termin hat; alle anderen erhalten `403`.
- Eine Nachricht enthält Absender, Text und Zeitstempel; sie erscheint im Nachrichtenverlauf des Termins und eingebettet in dessen Details.
- Neue Nachrichten lösen eine Benachrichtigung „<Name> schreibt zu <Termin>“ mit dem Nachrichtentext an die übrigen Teilnehmer aus ([M-007](../../05-modelle/M-007-benachrichtigungsmatrix.md)).
- Systemnachrichten (Zu-/Absage, Verschiebung, Terminabsage) stehen im selben Verlauf; Absender ist die auslösende Person ([FA-001](FA-001-termine-anlegen-aendern-verschieben-absagen-loeschen.md), [FA-003](FA-003-zu-und-absage-mit-mindestteilnehmerzahl.md), [FA-008](FA-008-terminaenderungen-nachvollziehbar-mitteilen.md)).
