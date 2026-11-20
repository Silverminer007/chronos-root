---
id: M-007
title: Benachrichtigungsmatrix
status: entwurf
typ: modell
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/WebPushService.java, backend/src/main/java/de/chronos_live/chronos_date_api/application/SettingsService.java]
verknuepft: [F-010, F-012, FA-004, FA-012]
---

# M-007: Benachrichtigungsmatrix

Stufe = Mindestrolle am Termin, ab der benachrichtigt wird (`ALL` = ab `GUEST`); die auslösende Person erhält nie eine Meldung zur eigenen Aktion.

| Ereignis | Empfänger | Einstellung (Standard) |
|---|---|---|
| Termin verschoben | Teilnehmer | `appointment_moved` (ALL) |
| Neue Nachricht | Teilnehmer | `appointment_message` (ATTENDANT) |
| Termin abgesagt | Teilnehmer | `appointment_cancelled` (ALL) |
| Person oder Gruppe eingeladen | Teilnehmer, auch die neue Person | `appointment_participant_added` (RESPONSIBLE) |
| Zu-/Absage eines Teilnehmers | Teilnehmer | `appointment_participation_status_changed` (RESPONSIBLE) |
| Termin hat zu wenige Teilnehmer | Teilnehmer, mit Zahl der Zusagen, Absagen, Offenen | `appointment_participation_invalid` (ATTENDANT) |
| Rückmeldung ausstehend (Erinnerung) | Teilnehmer mit Status `PENDING` | `appointment_participation_status_pending` (ATTENDANT) |
| Terminerinnerung 30 Minuten vorher | Teilnehmer | `appointment_reminder` (ALL); nie ausgelöst ([RA-005](../12-risiken-annahmen/RA-005-erinnerungen-teilweise-ohne-wirkung.md)) |
| Freundschaftsanfrage, -annahme, -ablehnung, -ende | jeweilige Gegenseite | nicht einstellbar |
| Zur Gruppe hinzugefügt | die neue Person | nicht einstellbar |
| Neues Gruppenmitglied | übrige Mitglieder | `group_member_added` (DISABLED) |
| Umfrage-Einladung | Benutzer, durch Administrator | nicht einstellbar |

Keine Meldung gibt es bei Rollenänderung, Entfernen aus Termin oder Gruppe, Anlegen, Ändern und Löschen eines Termins.
