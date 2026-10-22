# ARC42: Audit der API-Pfade im Angular-Frontend

Stand: 2026-10-22 (Ticket #211). Abgleich aller `/api/v2/*`-Aufrufe in `frontend-angular/src` mit den Rust-Routen
(`rust-backend/src/app.rs`, `admin/mod.rs`) und den Java-Resources (`AppointmentParticipationResource`, `AppointmentGroupResource`,
`Admin*Resource`). Rust-Bodies: `rust-backend/src/appointments/models/mod.rs`.

## Ursache

Der Appointment-Store und der Nuxt-Store (`frontend/app/stores/appointments.ts`) riefen Pfade auf, die keines der Backends kennt
(`participants/users`, `participants/groups`, `PATCH participants/users/{id}`). Das Backend antwortet 403/404. Die Pfade waren nie
gegen den Backend-Vertrag geprüft worden, weil die Unit-Tests nur den Soll-Wert des Stores selbst festschrieben und der BFF-Mock keine
Route ablehnte. Die E2E-Spec `appointment-participants.spec.ts` lässt den Mock nun wie das Backend alle unbekannten Pfade mit 403 beantworten.

## Termine

| Aktion | Aufruf (jetzt) | Backend | Status |
|---|---|---|---|
| Liste / Detail / Anlegen / Ändern / Löschen | `GET/POST /appointments`, `GET/PATCH/DELETE /appointments/{id}` | ✅ gleich | ✅ |
| Absagen des Termins | `POST /appointments/{id}/cancel` | ✅ | ✅ |
| Zu-/Absagen (#206) | `POST /appointments/{id}/participation` | ✅ | ✅ |
| Teilnehmer hinzufügen | `POST /appointments/{id}/participants/{userId}` `{user_role}` | `AddParticipantRequest` | korrigiert |
| Teilnehmer entfernen | `DELETE /appointments/{id}/participants/{userId}` | ✅ | korrigiert |
| Rolle ändern | `PUT /appointments/{id}/participation/{userId}/role` `{role}` | `ChangeParticipantRoleRequest` | korrigiert |
| Gruppe hinzufügen | `POST /appointments/{id}/groups/{groupId}` `{user_role}` (Java `AddGroupParticipantDto.user_role`; Rust akzeptiert `user_role` als Alias von `role`; `group_id` im Body ist in Java ungenutzt, die Gruppe kommt aus dem Pfad) | `AddGroupToAppointmentRequest` | korrigiert |
| Gruppe entfernen | `DELETE /appointments/{id}/groups/{groupId}` | ✅ | korrigiert |
| Nachricht senden | `POST /appointments/{id}/messages` `{body}` | ✅ | ✅ |

## Freunde, Gruppen, Einstellungen, Push, Nutzer

| Aufruf | Backend | Status |
|---|---|---|
| `GET /friendships/friends`, `DELETE /friendships/friends/{id}` | ✅ | ✅ |
| `GET /friendships/requests/incoming`, `/outgoing`, `POST /requests?email`, `POST /requests/{id}/accept`, `/decline`, `DELETE /requests/{id}` | ✅ | ✅ |
| `GET/POST /groups`, `DELETE /groups/{id}`, `GET /groups/{id}/users` | ✅ | ✅ |
| `POST/DELETE /groups/{id}/user/{userId}` | ✅ | ✅ |
| `GET /groups/?search=` | ✅ (Rust entfernt den Schrägstrich am Ende) | ✅ |
| `GET/PUT /settings` | ✅ | ✅ |
| `GET /push/public-key`, `POST /push/subscribe`, `GET /push/status` | ✅ | ✅ |
| `GET/PATCH /user` | ✅ | ✅ (`/me` existiert zusätzlich, wird nicht genutzt) |
| `GET /user/linked`, `DELETE /user/linked/{id}`, `GET /user/link/{provider}`, `/user/link/passkey`, `/user/unlink/passkey`, `/user/passkeys`, `/user/change-password` | ✅ | ✅ |

## Admin

| Aufruf | Backend | Status |
|---|---|---|
| `GET /admin/stats` (vorher `/admin/statistics`) | ✅ `/admin/stats` | korrigiert |
| `GET /admin/users?size=` (vorher `/admin/user/`) | ✅ `/admin/users` | korrigiert |
| `GET /admin/push/log` | ✅ | ✅ |
| `POST /admin/survey/{id}/notify` | ❌ in Rust und Java nicht vorhanden | Befund |

## Befunde ohne Backend-Route (kein Backend-Umbau in #211)

- `GET /survey/{id}/eligible` und `POST /survey/{id}/responses` (Umfrage, `survey.component.ts`) sowie `POST /admin/survey/{id}/notify`:
  weder Rust noch Java enthalten eine Survey-Route. Die Funktion antwortet gegen beide Backends mit 404. Folgeticket für Backend oder Entfernung im Frontend nötig.
- Die Felder der Antwort von `/admin/stats` und `/admin/users` wurden nicht gegen die DTOs geprüft, nur die Pfade.

## Entscheidung

Pfade und Bodies richten sich allein nach dem Backend-Vertrag; der Nuxt-Store wurde mit korrigiert (gleicher Fehler). Die Unit-Tests
(`appointment.store.spec.ts`, `admin.component.spec.ts`) prüfen Pfad, Methode und Body.
