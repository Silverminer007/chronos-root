---
id: FA-006
title: Freundschaften und Gruppen verwalten
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/FriendshipService.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/GroupService.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java]
verknuepft: [F-006, F-008]
---

# FA-006: Freundschaften und Gruppen verwalten

*Als Benutzer möchte ich Freundschaften und Gruppen verwalten, damit ich Personen gebündelt einladen kann.*

## Akzeptanzkriterien: Freundschaften
- Eine Anfrage geht an eine Benutzer-ID oder an eine E-Mail-Adresse. Die Adresse muss (ohne Beachtung der Groß-/Kleinschreibung) einem Konto entsprechen, sonst erscheint „Es wurde kein User mit der E-Mail Adresse … gefunden“.
- Abgelehnt wird die Anfrage an sich selbst, an unbekannte Benutzer, bei bestehender Freundschaft, bei bereits gesendeter Anfrage und bei offener Gegenanfrage (Hinweis, diese anzunehmen). Eine abgelehnte Anfrage darf erneut gesendet werden.
- Nur die empfangende Person nimmt an oder lehnt ab, nur die sendende Person zieht eine offene Anfrage zurück; bereits bearbeitete Anfragen sind unveränderlich. Zurückziehen löscht die Anfrage.
- Jede Seite kann eine bestehende Freundschaft beenden; die Gegenseite wird benachrichtigt.
- Eingehende und ausgehende Anfragen sind abrufbar, neueste zuerst; die Freundesliste ist nach Name sortiert und zeigt Name, E-Mail, Profilbild und „befreundet seit“.
- Vorschläge stammen aus der Benutzersuche und enthalten weder die eigene Person noch bestehende Freunde (Schnittstelle; die Oberfläche nutzt sie nicht).
- Die Freundesseite hat zwei Reiter, Freunde sowie eingehende und ausgehende Anfragen, mit Leerzuständen („Noch keine Freunde“, „Keine eingehenden Anfragen“, „Keine ausgehenden Anfragen“); „Freund hinzufügen“ nimmt eine E-Mail-Adresse entgegen, „Freundschaft beenden“ verlangt eine Bestätigung.
- Anfrage, Annahme, Ablehnung und Beenden lösen eine nicht abschaltbare Benachrichtigung an die jeweilige Gegenseite aus ([M-007](../../05-modelle/M-007-benachrichtigungsmatrix.md)).

## Akzeptanzkriterien: Gruppen
- Jeder Benutzer legt eine Gruppe mit nicht leerem Namen an; er ist deren Besitzer und erstes Mitglied.
- Nur der Besitzer benennt die Gruppe um oder löscht sie; das Löschen entfernt auch die Mitgliedschaften.
- Mitglieder dürfen ihre Freunde zur Gruppe hinzufügen (nicht mehrfach); Nichtmitglieder erhalten `403`.
- Nur der Besitzer entfernt Mitglieder, nie sich selbst; ein Mitglied kann die Gruppe daher nicht verlassen (`403`). Die Oberfläche bietet dennoch „Gruppe verlassen“ und erlaubt jedem Mitglied, andere zu entfernen; beides scheitert am Backend ([RA-003](../../12-risiken-annahmen/RA-003-frontend-und-backend-schnittstellen-weichen-ab.md)).
- Nur Mitglieder sehen die Mitgliederliste; Gruppen sind nach Namen durchsuchbar (Schnittstelle; die Gruppenseite bietet keine Suche).
- Die Oberfläche legt Gruppen an und zeigt Gruppenliste, Mitglieder mit Anzahl und „Mitglied hinzufügen“ aus der Freundesliste; „Gruppe löschen“ erscheint nur, wenn die Person das einzige Mitglied ist, und gelingt nur dem Besitzer. Eine Oberfläche zum Umbenennen gibt es nicht.
- Das neue Mitglied erhält immer eine Benachrichtigung; die übrigen Mitglieder nur, wenn sie „Mitglied hinzugefügt“ eingeschaltet haben (Standard aus).
