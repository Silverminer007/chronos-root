---
id: TD-014
title: "Frontend ruft Pfade ohne Backend-Gegenstück"
status: entwurf
typ: technische-schuld
quellen:
  - frontend/app/stores/appointments.ts
  - frontend/app/pages/admin/index.vue
  - frontend/app/pages/survey/[surveyId].vue
  - frontend/public/push-sw.js
  - backend/src/main/java/de/chronos_live/chronos_date_api/presentation/AppointmentParticipationResource.java
  - backend/src/main/java/de/chronos_live/admin/presentation/
verknuepft: [RA-003, BB-003, BB-006, BB-015, LZ-004]
---

# TD-014: Frontend ruft Pfade ohne Backend-Gegenstück

Mehrere Aufrufe des Frontends treffen keine Backend-Route:

| Frontend (`/api/v2/…`) | Backend bietet |
|---|---|
| `appointments/{id}/participants/approve`, `…/reject` (Store, Service Worker) | `POST appointments/{id}/participation` |
| `appointments/{id}/participants/status` (Service Worker, Schnellaktion) | nur `GET …/participation/status` |
| `appointments/{id}/participants/users`, `…/users/{userId}` | `POST`/`DELETE …/participants/{userId}`, `PUT …/participation/{userId}/role` |
| `appointments/{id}/participants/groups`, `…/groups/{groupId}` | `POST`/`DELETE appointments/{id}/groups/{groupId}` |
| `admin/statistics`, `admin/user/` | `admin/stats`, `admin/users` |
| `survey/{surveyId}/eligible`, `…/responses`, `admin/survey/…/notify` (Seite `survey/[surveyId]`, Admin) | keine Survey-Route im Backend |

**Auswirkung:** Zu-/Absage, Teilnehmer und Gruppen einladen sowie Teile der Admin-Seite scheitern mit 404, soweit die Aufrufe so im Betrieb ankommen; gegen die laufende Produktion nicht geprüft **(?)**. Die Schnellaktion Zusagen/Absagen in der Push-Nachricht ist ebenfalls betroffen (G-013). Dieselbe Lücke beschreibt [RA-003](../../req42/12-risiken-annahmen/RA-003-frontend-und-backend-schnittstellen-weichen-ab.md).

**Maßnahme:** Frontend an die Backend-Pfade anpassen (oder Backend-Routen ergänzen) und die Schnittstelle per Vertragstest absichern; die Survey-Seite entweder an ein Backend anbinden oder entfernen.
