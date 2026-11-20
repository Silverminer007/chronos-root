---
id: FA-007
title: Admin-API
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/admin/presentation/, backend/src/main/java/de/chronos_live/chronos_date_api/security/PrincipalContextFilter.java, frontend/app/pages/admin/]
verknuepft: [F-015]
---

# FA-007: Admin-API

*Als Administrator möchte ich einen eigenen Zugang zur Betreuung, damit ich Daten prüfen und eingreifen kann.*

## Akzeptanzkriterien
- Aufrufe der Admin-Funktionen ohne Anmeldung werden mit `401`, ohne die Rolle `ADMIN_API` mit `403` abgelehnt.
- Admin-Aufrufe umgehen die Teilnehmer-, Besitzer- und Freundschaftsprüfungen der normalen Funktionen.
- Benutzer auflisten (seitenweise), einzeln abrufen und löschen; ein unbekannter Benutzer ergibt `404`.
- Termine anlegen und auflisten, Gruppen als Teilnehmer hinzufügen, den Teilnahmestatus einer Person ändern und Nachrichten senden.
- Gruppen anlegen, Mitglieder hinzufügen und Freundschaften herstellen.
- Statistik: Anzahl Benutzer, Termine (gesamt und je Status), Teilnahmen (gesamt und je Status), Gruppen und Mitglieder, Freundschaften (gesamt und je Status), Nachrichten, Push-Abos.
- Test-Push an einen Benutzer senden (mit eigener oder Standardnachricht) und das Push-Protokoll nach Benutzer, Zeitraum, Erfolg und Ereignistyp filtern (Standard 50 Einträge je Seite).
- Die Oberfläche unter `/admin` bietet Statistik, Benutzerliste (20 je Seite), Push-Protokoll unter `/admin/push` und den Versand der Umfrage-Einladung ([FA-015](FA-015-nutzerumfrage-beantworten.md)); sie ist nur per Adresse erreichbar, im Benutzermenü gibt es keinen Eintrag.
