---
id: FA-012
title: Benachrichtigungen konfigurieren
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/SettingsService.java, backend/src/main/java/de/chronos_live/chronos_date_api/dto/SettingsDto.java, frontend/app/pages/settings.vue]
verknuepft: [F-012]
---

# FA-012: Benachrichtigungen konfigurieren

*Als Benutzer möchte ich je Ereignisart festlegen, ab welcher Rolle ich benachrichtigt werde, damit ich nur Relevantes erhalte.*

## Akzeptanzkriterien
- Einstellbar sind die Ereignisarten `appointment_moved`, `appointment_message`, `appointment_cancelled`, `appointment_participant_added`, `appointment_participation_status_changed`, `appointment_participation_invalid`, `appointment_participation_status_pending`, `appointment_reminder` mit den Werten `DISABLED`, `ALL`, `ATTENDANT`, `HELPER`, `RESPONSIBLE`, sowie `group_member_added` mit `ENABLED` oder `DISABLED`.
- Ein Wert ist die Mindestrolle: Benachrichtigt wird, wer am Termin mindestens diese Rolle hat (`ALL` = ab `GUEST`, `DISABLED` = nie); Rollenordnung siehe [M-006](../../05-modelle/M-006-rollenmatrix.md).
- Standardwerte gelten, bis die Person speichert (sie werden beim ersten Zugriff angelegt): siehe [M-007](../../05-modelle/M-007-benachrichtigungsmatrix.md).
- Die Einstellungsseite ändert jede Ereignisart einzeln, speichert gesammelt und schaltet Push für das aktuelle Gerät ein oder aus; Berechtigungsverweigerung im Browser wird mit Hinweis angezeigt.
