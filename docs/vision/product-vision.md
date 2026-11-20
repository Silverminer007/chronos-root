---
title: Produktvision
status: entwurf   # Menschen: nach Prüfung auf "freigegeben" setzen
freigegeben-am:
freigegeben-von:
quellen:
  - frontend/app/pages/index.vue
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/
---

# Produktvision

> ENTWURF, rückwärts aus dem Ist-Zustand abgeleitet (Landing-Seite, Domänenmodell, Funktionen). Aussagen mit **(?)** sind Annahmen und müssen von einem Menschen bestätigt oder ersetzt werden.

## Vision (?)

Jugendverbände planen Termine gemeinsam, ohne hinterherzutelefonieren: Wer eingeladen ist, sagt zu oder ab, und alle sehen sofort, ob die Veranstaltung stattfinden kann.

## Zielgruppe

- Jugendverbände: Gruppenleitungen und Verantwortliche, die Termine anlegen (Rolle `RESPONSIBLE`). Die Landing-Seite wirbt mit „Für Jugendverbände gemacht".
- Mitglieder, Helfer:innen und Gäste, die Termine bestätigen und Erinnerungen erhalten (Rollen `ATTENDANT`, `HELPER`, `GUEST`).
- Administrator:innen mit der Rolle `ADMIN_API`, die Termine, Gruppen, Benutzer und Push-Versand verwalten und auswerten (Admin-API).

## Kernnutzen

- Termine anlegen und Freunde oder ganze Gruppen einladen.
- Zu-/Absage und Mindestteilnehmerzahl im Blick behalten; der Termin meldet selbst, wenn zu wenige zugesagt haben.
- Push-Benachrichtigungen und automatische Rückmelde-Erinnerungen statt Nachfragen, einstellbar je Ereignisart.
- Als installierbare Web-App (PWA) auf dem Smartphone nutzbar.

## Abgrenzung (?)

Chronos ist kein allgemeiner Kalender und kein Chat-Ersatz; Nachrichten gibt es nur im Kontext eines Termins.

## Erfolgskriterien (?)

- Offen: Welche Kennzahlen zeigen, dass Chronos hilft (z. B. Anteil beantworteter Einladungen, aktive Gruppen)?

## Offene Fragen an den Product Owner

- [ ] Vision und Abgrenzung bestätigen.
- [ ] Erfolgskriterien festlegen.
