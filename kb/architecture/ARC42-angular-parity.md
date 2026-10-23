# ARC42: Paritätsliste Nuxt ↔ Angular

Stand: 2026-10-22 (Ticket #188). Vergleich von `frontend/` (Nuxt) mit `frontend-angular/`. Grundlage sind die Nuxt-Dateien unter
`frontend/app/` und `frontend/server/` sowie die ADRs ADR-2026-10-21-002 bis -014. Fehlende Funktionen sind hier vermerkt und nicht
im Ticket #188 nachgebaut worden.

Legende: ✅ vorhanden und getestet · ➖ bewusste Abweichung (begründet) · ❌ fehlt

## Seiten und Routen

| Nuxt | Angular | Status | Anmerkung |
|---|---|---|---|
| `pages/index.vue` (Landing) | `features/public/landing` | ✅ | E2E `landing.spec.ts` |
| `pages/public/{impressum,datenschutz,install}.vue` | `features/public/*` | ✅ | E2E `public-pages.spec.ts` |
| `pages/agenda.vue` | `features/agenda` | ✅ | E2E `agenda.spec.ts` (Liste, Mehr laden, Suche, Zusagen) |
| `pages/appointment/[appointmentId].vue` | `features/appointment` | ✅ | Unit-Tests |
| `pages/friends.vue` | `features/friends` | ✅ | E2E `friends.spec.ts` |
| `pages/groups/index.vue`, `[groupId].vue` | `features/groups` | ✅ | E2E `groups.spec.ts` |
| `pages/settings.vue` | `features/settings` | ✅ | Unit-Tests |
| `pages/profile.vue` | `features/profile` | ✅ | Unit-Tests |
| `pages/onboarding.vue` | `features/onboarding` | ✅ | Unit-Tests |
| `pages/survey/[surveyId].vue` | `features/survey` | ✅ | Unit-Tests |
| `pages/admin/index.vue`, `push.vue` | `features/admin` | ✅ | kein Rollen-Guard, wie Nuxt (ADR-2026-10-21-013) |
| `pages/mockup/*` | – | ➖ | Entwicklungs-Prototypen, keine Produktionsseiten |

## Komponenten

| Nuxt | Angular | Status |
|---|---|---|
| `AppointmentCard`, `AppointmentCardSkeleton`, `SearchHeader` | `features/agenda/*` | ✅ |
| `AppointmentDemoCard`, `LandingFooter` | `features/public/landing`, `layout/` | ✅ |
| `AppointmentHeader`, `AppointmentDetailsCard`, `AppointmentResponsibleActions`, `OwnParticipationStatusCard`, `ParticipantsStatusCard`, `GroupParticipantsCard`, `MessagesCard`, `AddParticipantDialog`, `AddGroupDialog` | `features/appointment/*` | ✅ |
| `CreateAppointmentStepper`, `EditAppointmentBottomSheet`, `AppointmentParticipantPicker`, `composables/appointmentForm.ts` | `features/appointment-form/*` | ✅ |
| `FriendCard`, `FriendRequestCard`, `SendFriendRequestDialog` | `features/friends/*` | ✅ |
| `GroupCard`, `GroupMemberCard`, `CreateGroupDialog`, `AddGroupMemberDialog` | `features/groups/*` | ✅ |
| `Notification*SettingsCard`, `NotificationPushCard` | `features/settings/*` | ✅ |
| `PushPermissionModal`, `NewFeaturesDialog`, `PwaInstallDialog` | `features/onboarding`, `core/` | ✅ |
| `ConfirmDialog`, `MessageDialog`, PrimeVue-Toast | `core/confirm-dialog`, `shared/message-dialog`, `core/toast-*` | ✅ |

## Stores, Composables, Server

| Nuxt | Angular | Status | Anmerkung |
|---|---|---|---|
| Pinia-Stores `appointments`, `friendships`, `groups`, `settings`, `auth` | Signal-Stores bzw. `AuthService` | ✅ | |
| `middleware/auth.global.ts`, `plugins/auth.client.ts` | `authGuard`, `AuthService` (`SESSION_EXPIRED`) | ✅ | |
| `plugins/register-sw.client.ts`, `public/push-sw.js` | `core/service-worker.ts`, `public/push-sw.js`, `sw-core.js` | ✅ | gleicher Pfad `/push-sw.js`, Unit-Tests `sw-tests/` |
| `server/api/auth/{login,callback,register,refresh,logout,isLoggedIn}` | `server/app.ts` | ✅ | `server/*.spec.ts`; E2E `login.spec.ts` |
| `server/api/v2/[...path].ts` | `server/app.ts` (Proxy) | ✅ | |
| `useDateFormatter` / Luxon | `core/date-format.ts` (`Intl`) | ➖ | Unterschiede nur an Monats-/Jahresgrenzen der relativen Zeit (ADR-013) |
| PrimeVue `DatePicker` | `<input type="date">` / `datetime-local` | ➖ | keine Zusatzabhängigkeit |
| Lucide/Simple-Icons, Tailwind, PrimeVue | Bootstrap Icons, Bootstrap 5 | ➖ | Vorgabe REQ42 |
| `useAppointmentShare` | `appointment-share.service.ts` | ✅ | |

## Bekannte Lücken

| Punkt | Status | Anmerkung / Empfehlung |
|---|---|---|
| `public/robots.txt` (`User-agent: * / Disallow:`) | ❌ | fehlt in `frontend-angular/public/`; der BFF liefert dafür aktuell keine Datei aus. Vor dem Cutover als Datei in `public/` nachziehen (eigenes Ticket). |
| `public/_headers` (Content-Type des Manifests) | ➖ | nicht nötig: der BFF liefert `/manifest.webmanifest` mit `application/manifest+json` (E2E `public-pages.spec.ts`) |
| Sentry (`NUXT_PUBLIC_SENTRY_DSN`, `SENTRY_AUTH_TOKEN`) | ➖ | im Nuxt-Frontend nur als Konfigurationswert ohne Modul und ohne Nutzung im Code; Angular liest die Werte nicht. Das Chart darf sie weiter setzen, sie werden ignoriert. |
| Lighthouse-Kategorie „PWA“ | ➖ | seit Lighthouse 12 entfernt. Ersatz: E2E prüft Manifest (Pflichtfelder, Content-Type) und Service-Worker-Pfad; Lighthouse prüft Barrierefreiheit, SEO, Best Practices (siehe unten). |
| Performance-Score ≈ 0,5 (Lighthouse, mobil simuliert, Entwicklungs-VM) | ❌ | LCP ≈ 6 s auf Landing/Impressum/Install, vor allem durch Bundle- und CSS-Größe unter simulierter 4G-Drosselung. Nur Warnung (`minScore 0.7`). Ein Vergleich mit dem Nuxt-Frontend wurde nicht gemessen. Vor dem Cutover entscheiden, ob eine Optimierung (eigenes Ticket) nötig ist. |
| Mockup-Seiten | ➖ | nicht portiert |

## Testabdeckung der Kernflüsse (Q-06)

| Fluss | Test |
|---|---|
| Login-Redirect (Keycloak) und Weiterleitung nicht angemeldeter Besucher | `e2e/login.spec.ts`, `e2e/auth-redirects.spec.ts` |
| Agenda (Liste, Paging, Suche, Zusage) | `e2e/agenda.spec.ts` |
| Termin anlegen (Stepper bis `POST /api/v2/appointments`, Weiterleitung) | `e2e/appointment-create.spec.ts` |
| Freunde (Liste, Anfrage senden, Anfragen-Tab) | `e2e/friends.spec.ts` |
| Gruppen (anlegen, Liste) | `e2e/groups.spec.ts` |
| Öffentliche Seiten, PWA-Manifest, 360-px-Darstellung | `e2e/public-pages.spec.ts`, `landing.spec.ts` |

Alle E2E-Tests laufen gegen den echten BFF mit `dist/browser`; Sitzung und `/api/v2/*` werden per `page.route` gemockt
(`e2e/support/mock-session.ts`), Backend und Keycloak sind nicht nötig.
