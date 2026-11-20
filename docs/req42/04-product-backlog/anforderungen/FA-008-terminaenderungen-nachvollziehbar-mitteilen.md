---
id: FA-008
title: Terminänderungen nachvollziehbar mitteilen
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/MessageService.java, backend/src/test/java/de/chronos_live/chronos_date_api/application/MessageServiceMovedTextTest.java]
verknuepft: [F-001, F-009]
---

# FA-008: Terminänderungen nachvollziehbar mitteilen

*Als Teilnehmer möchte ich bei Verschiebungen erfahren, um wie viel sich der Termin ändert, damit ich entscheiden kann, ob ich weiter teilnehme.*

## Akzeptanzkriterien
- Verschiebt sich der Start, lautet die Nachricht „<Name> hat diesen Termin um <Dauer> nach hinten verschoben“ bzw. „… vorverlegt“; bleibt der Start gleich und ändert sich das Ende, lautet sie „<Name> hat das Ende dieses Termins um <Dauer> …“. Der Start hat Vorrang.
- Die Dauer wird in Stunden und Minuten angegeben („1 Stunde 30 Minuten“, „45 Minuten“); die Einzahl lautet „1 Stunde“ bzw. „1 Minute“.
- Beträgt die Änderung weniger als eine Minute, entsteht keine Nachricht.
- Die Push-Benachrichtigung zur Verschiebung nennt den neuen Zeitraum ([M-007](../../05-modelle/M-007-benachrichtigungsmatrix.md)).
