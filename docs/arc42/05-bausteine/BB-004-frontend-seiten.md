---
id: BB-004
title: Frontend: Seiten (Ebene 2)
status: entwurf
typ: baustein-ebene-2
quellen:
  - frontend/app/pages
verknuepft: [BB-001]
---

# Frontend: Seiten (Ebene 2)

| Seite | Zweck |
|---|---|
| `index.vue` | Startseite / Landing |
| `agenda.vue` | Terminübersicht |
| `appointment/[appointmentId].vue` | Termindetails, Teilnehmer, Nachrichten |
| `groups/index.vue`, `groups/[groupId].vue` | Gruppen |
| `friends.vue` | Freunde und Anfragen |
| `profile.vue`, `settings.vue` | Profil, Benachrichtigungen |
| `onboarding.vue` | Profil vervollständigen |
| `survey/[surveyId].vue` | Umfrage **(?)** |
| `admin/index.vue`, `admin/push.vue` | Administration |
| `public/*` | Impressum, Datenschutz, Installation |
| `mockup/` | Prototypen, keine Produktionsseiten |

Öffentliche Seiten nutzen das Layout `landingpage`, alle anderen setzen Anmeldung voraus.
