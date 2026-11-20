---
id: RA-003
title: Frontend und Backend nutzen unterschiedliche Schnittstellen
status: entwurf
typ: risiko
quellen: [frontend/app/stores/appointments.ts, frontend/app/pages/admin/index.vue, backend/src/main/java/de/chronos_live/chronos_date_api/presentation/AppointmentParticipationResource.java, backend/src/main/java/de/chronos_live/admin/presentation/]
verknuepft: [FA-002, FA-003, FA-006, FA-007, FA-010]
---

# RA-003: Frontend und Backend nutzen unterschiedliche Schnittstellen

**Risiko:** Das Nuxt-Frontend ruft für Zu-/Absage (`participants/approve|reject`), Teilnehmer und Gruppen einladen (`participants/users`, `participants/groups`), Rolle ändern (`PATCH participants/users/{id}`) sowie Admin-Statistik und -Benutzer (`admin/statistics`, `admin/user/`) Pfade auf, die das Java-Backend seit der Umstellung (Commit „Implement participation/RSVP endpoints (#31)“) nicht mehr anbietet (dort `POST participation`, `PUT participation/{id}/role`, `POST participants/{id}`, `POST groups/{id}`, `admin/stats`, `admin/users`). Kernfunktionen wie Zu-/Absage sind dann in der Oberfläche wirkungslos. Außerdem bietet die Oberfläche „Gruppe verlassen“ und erlaubt jedem Mitglied, andere zu entfernen, während das Backend beides ablehnt (`403`, [FA-006](../04-product-backlog/anforderungen/FA-006-freundschaften-und-gruppen-verwalten.md)). **(?)** Ob die laufende Produktion einen Stand verwendet, der beides noch vereint, ist aus dem Repository nicht erkennbar; bitte gegen Produktion prüfen.

**Wahrscheinlichkeit / Schaden:** hoch (der Widerspruch ist im Code belegt) / hoch (Kernfunktion). **Maßnahme:** Frontend an die Backend-Schnittstelle anpassen oder umgekehrt; Vertragstest zwischen beiden. Technische Schuld: [TD-014](../../arc42/11-risiken/TD-014-frontend-ruft-pfade-ohne-backend-gegenstueck.md).
