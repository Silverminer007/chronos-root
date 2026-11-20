---
id: AE-050
title: "Termine per Statuswechsel löschen, Teilnahmen hart"
status: entwurf
typ: architekturentscheidung
datum: 2025-12-27
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentParticipationService.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/AppointmentRepository.java
  - backend/src/main/resources/db/migration/V1.4.0__delete_group_members_on_group_delete.sql
verknuepft: [G-002, AE-048, AE-055]
---

# AE-050: Termine per Statuswechsel löschen, Teilnahmen hart

**Entscheidung:**
- `DELETE /api/v2/appointments/{id}` löscht keine Zeile, sondern setzt `AppointmentStatus.DELETED`; `POST …/cancel` setzt `CANCELLED`. Lesende Abfragen (Termin, Liste) schließen `DELETED` aus; `CANCELLED` bleibt sichtbar; die Erinnerungssuche schließt beide aus.
- Teilnahmen (`AppointmentParticipation`) und Gruppenteilnahmen werden dagegen hart gelöscht (`deleteByAppointmentAndUser`, `deleteGroupParticipation`); beim Entfernen einer Gruppe vom Termin entfallen auch die von ihr abgeleiteten Teilnahmen. Ein nicht vorhandener Eintrag ergibt einen Fehler. Gruppenmitglieder verschwinden beim Löschen der Gruppe per Datenbank-Cascade (V1.4.0).

**Begründung:** nicht dokumentiert **(?)**. Erkennbar: Löschen und Absagen lösen Ereignisse aus (`AppointmentDeletedEvent`, `AppointmentCancelledEvent`), und Termine bleiben nach Status zählbar (`countByStatus`, Admin-Statistik).

**Alternativen:** Hartes Löschen von Terminen; Soft-Delete auch für Teilnahmen.

**Konsequenzen:** Gelöschte Termine bleiben in der Datenbank, Datenschutz-Löschkonzept offen **(?)**; jede neue Abfrage muss `DELETED` ausschließen; entfernte Teilnahmen sind nicht wiederherstellbar.
