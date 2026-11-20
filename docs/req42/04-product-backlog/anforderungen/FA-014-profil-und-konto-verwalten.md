---
id: FA-014
title: Profil und Konto verwalten
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/presentation/UserResource.java, frontend/app/pages/profile.vue, frontend/app/stores/auth.ts]
verknuepft: [F-014]
---

# FA-014: Profil und Konto verwalten

*Als Benutzer möchte ich Profil, verknüpfte Konten, Passkeys und Passwort verwalten, damit ich meine Zugangsdaten selbst pflege.*

## Akzeptanzkriterien
- Vorname, Nachname und E-Mail-Adresse sind änderbar; erfordert die Änderung eine Verifizierung der E-Mail-Adresse, wird die Person dorthin geleitet.
- Verknüpfte Konten werden angezeigt und lassen sich verknüpfen und lösen; angeboten wird Google (Microsoft und Apple sind im Code deaktiviert).
- Passkeys werden aufgelistet und lassen sich hinzufügen und löschen; beides erfolgt über die Keycloak-Kontoverwaltung mit Rücksprung nach Chronos.
- Das Passwort wird über Keycloak mit Rücksprung nach Chronos geändert.
- Name, E-Mail und Profilbild anderer Personen entsprechen dem zuletzt bei Keycloak gelesenen Stand; ein bei Keycloak gelöschter Benutzer erscheint als „Gelöschter Benutzer“.
- Ein Löschen des eigenen Kontos ist nicht möglich (Aufruf wird mit `405` abgelehnt, Oberfläche bietet es nicht an; Folgen für den Datenschutz: [RA-004](../../12-risiken-annahmen/RA-004-datenschutzerklaerung-verspricht-mehr-als-die-anwendung-leistet.md)).
