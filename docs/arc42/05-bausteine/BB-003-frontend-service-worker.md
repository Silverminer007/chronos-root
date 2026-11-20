---
id: BB-003
title: "Frontend: Service Worker (Ebene 2)"
status: entwurf
typ: baustein-ebene-2
quellen:
  - frontend/public/push-sw.js
  - frontend/app/plugins/auth.client.ts
  - frontend/app/plugins/register-sw.client.ts
verknuepft: [LS-004, LZ-002, KT-009, KT-010, BB-013, TD-014, TD-015, TD-017]
---

# Frontend: Service Worker (Ebene 2)

Eine Datei `public/push-sw.js`, registriert von `register-sw.client.ts` (Update-Prüfung stündlich); `install` ruft `skipWaiting`, `activate` `clients.claim` und plant den ersten Refresh. Lädt Luxon per `importScripts` von jsDelivr ([KT-010](../03-kontext/KT-010-jsdelivr.md)).

**Fetch-Handler** (jede Anfrage):
- Pfade außerhalb von `/api` werden durchgereicht.
- `/api/auth/isLoggedIn` beantwortet er selbst aus `cookieStore`: Restlaufzeit unter 5 s löst einen Refresh aus, Erfolg 204, sonst 401.
- Übrige `/api/auth/*` gehen unverändert durch.
- Alle anderen `/api/*`: Refresh bei Restlaufzeit unter 5 s (Fehler werden ignoriert), nach 401 ein Refresh und eine Wiederholung.

**Refresh-Steuerung:** Timer 30 s vor `kc_expires` (neu geplant nach jedem Refresh und bei Änderung des Cookies), gleichzeitige Refreshes teilen ein Promise (`inflightRefresh`). Löscht der Server `kc_expires`, sendet er `SESSION_EXPIRED` an alle Fenster ([BB-001](BB-001-frontend.md) Plugin `auth.client.ts`).

**Push-Anzeige:** `push`-Ereignis → `handleNotification` formatiert Titel, Text und Aktionsknöpfe nach `type` der Nutzlast (Typen: [BB-013](BB-013-push-benachrichtigungen.md); zusätzlich `SURVEY_INVITATION`, `GROUP_MEMBER_LEFT`); Nutzlasten ohne bekannten Typ zeigen `title` und `body`. Zusage-/Absage-Aktionen rufen das Backend, ein Klick öffnet `/appointment/{id}`, `/groups/{id}` oder `/survey/{id}`. Vor der Anzeige fragt er den eigenen Teilnahmestatus ab. Unbehandelte Fehler meldet er als `SW_ERROR` an die Fenster ([KT-008](../03-kontext/KT-008-sentry.md)).

**Abweichung von der Backend-Schnittstelle:** Die Aktionsknöpfe und die Statusabfrage nutzen `…/participants/status`, `…/participants/approve` und `…/participants/reject`; im Backend existieren stattdessen `POST …/participation` und `GET …/participation/status` ([BB-006](BB-006-java-schichten.md), Abschnitt „Abweichung Frontend und Backend“). Für `NEW_PARTICIPANT` liest der Service Worker `new_attendee`, das Backend sendet `new_participant`; die Anzeige nennt dann keinen Namen. Risiko: [req42 RA-003](../../req42/12-risiken-annahmen/RA-003-frontend-und-backend-schnittstellen-weichen-ab.md).
