---
id: BB-004
title: "Frontend: Seiten (Ebene 2)"
status: entwurf
typ: baustein-ebene-2
quellen:
  - frontend/app/pages
  - frontend/app/layouts
verknuepft: [BB-001, BB-015, BB-014]
---

# Frontend: Seiten (Ebene 2)

| Seite | Zweck |
|---|---|
| `index.vue` | Landingpage mit Anmelden und Registrieren (Layout `landingpage`) |
| `agenda.vue` | Terminübersicht mit Suche und Nachladen |
| `appointment/[appointmentId].vue` | Termindetails, Teilnehmer, Gruppen, Nachrichten, Aktionen für Verantwortliche |
| `groups/index.vue`, `groups/[groupId].vue` | Gruppen und Mitglieder |
| `friends.vue` | Freunde und Anfragen |
| `profile.vue` | Profil, verknüpfte Konten, Passkeys, Passwort |
| `settings.vue` | Benachrichtigungseinstellungen und Push |
| `onboarding.vue` | Erste Schritte, erreicht bei erster Nutzung über die Middleware |
| `survey/[surveyId].vue` | Nutzerumfrage; ruft `/api/v2/survey/…` auf; diese Routen gibt es im Backend nicht ([req42 RA-006](../../req42/12-risiken-annahmen/RA-006-nutzerumfrage-ohne-backend.md)) |
| `admin/index.vue`, `admin/push.vue` | Administration ([BB-015](BB-015-admin-bereich.md)) |
| `public/impressum.vue`, `datenschutz.vue`, `install.vue` | Ohne Anmeldung erreichbar |

Seiten außer `index.vue` und `public/*` setzen eine Sitzung voraus ([BB-001](BB-001-frontend.md) Middleware).
