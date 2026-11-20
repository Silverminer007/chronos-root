---
id: BB-014
title: "Frontend: Pinia-Stores und Composables (Ebene 2)"
status: entwurf
typ: baustein-ebene-2
quellen:
  - frontend/app/stores
  - frontend/app/composables
  - frontend/app/utils/errors.ts
verknuepft: [BB-001, KZ-005, KZ-011, LS-001]
---

# Frontend: Pinia-Stores und Composables (Ebene 2)

Fachlogik und Mutationen (`$fetch`) liegen in Stores; Komponenten rufen nur Store-Aktionen. Fehler werden mit `getErrorMessage(err, <deutscher Fallback>)` abgeleitet: Vorrang hat `data.message` der Backend-Antwort ([KZ-005](../08-konzepte/KZ-005-fehlerbehandlung.md)).

| Store | Verantwortung |
|---|---|
| `auth.ts` (Setup-Store) | Sitzung (`checkSession` über `/api/auth/isLoggedIn`), Benutzer, Profil ändern, Passwort, verknüpfte Konten, Passkeys, Logout |
| `appointments.ts` | Agenda mit Filtern und Paginierung, Termin laden, anlegen, ändern, absagen, löschen, Teilnehmer, Gruppen, Rollen, Zu-/Absage, Nachrichten |
| `groups.ts` | Gruppen und Mitglieder |
| `friendships.ts` | Freunde, eingehende und ausgehende Anfragen |
| `settings.ts` | Benachrichtigungseinstellungen |

| Composable | Aufgabe |
|---|---|
| `usePush` | Push-Erlaubnis erfragen, Abo anlegen und prüfen (Schlüssel `pushPromptShownAt`, `pushPromptAnswer`) |
| `useOnboarding` | Erste-Schritte-Flag (`onboardingShownAt`) |
| `useNewFeatures` | Einmalhinweise zu neuen Funktionen (`shownFeatureIds`) |
| `useFriends`, `useGroups` | Listen für Auswahldialoge |
| `appointmentForm`, `useAppointmentShare`, `useDateFormatter` | Formularzustand, Teilen, Datumsformate |

Gemeinsame Typen: `app/types/index.ts`.
