---
id: BB-014
title: Frontend: Pinia-Stores (Ebene 2)
status: entwurf
typ: baustein-ebene-2
quellen:
  - frontend/app/stores
verknuepft: [BB-001, KZ-005]
---

# Frontend: Pinia-Stores (Ebene 2)

Fachlogik und Mutationen (`$fetch`) liegen in Stores, Komponenten rufen nur Store-Aktionen.

| Store | Verantwortung |
|---|---|
| `auth.ts` | Sitzung, Login, `checkSession()` |
| `appointments.ts` | Termine, Teilnahme, Nachrichten |
| `groups.ts` | Gruppen und Mitglieder |
| `friendships.ts` | Freunde, Anfragen |
| `settings.ts` | Benachrichtigungseinstellungen |

Gemeinsame Typen: `app/types/index.ts`.
