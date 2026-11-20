---
id: AE-010
title: "Geschichtetes Backend mit Ports"
status: entwurf
typ: architekturentscheidung
datum: 2025-09-28
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/
  - backend/src/main/java/de/chronos_live/admin/
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/ports/
  - kb/adr/ADR-2025-09-28-007-layered-backend.md (Git-Historie, Begründung und Alternativen)
verknuepft: [AE-048, AE-049, AE-051]
---

# AE-010: Geschichtetes Backend mit Ports

**Entscheidung:** Das Backend ist nach Schichten geschnitten; Abhängigkeiten zeigen nach unten.

| Schicht (Paket) | Inhalt |
|---|---|
| `presentation` | JAX-RS-Resources, nehmen und liefern DTOs (`dto`, `mapper` mit MapStruct) |
| `application` | Services mit Geschäftslogik, Autorisierung (`AuthorizationService`), Events, Reminder-Regeln, Scheduler |
| `infrastructure` | Panache-Repositories, Adapter (`WebPushAdapter`, `LocalDbIdentityAdapter`) |
| `domain` | Entities und Enums |

Die Admin-API liegt in einem eigenen Paket `de.chronos_live.admin` mit denselben Schichten. Zu externen Systemen definiert `application.ports` Schnittstellen (`IdentityPort`, `NotificationPort`, `NotificationLogPort`, `ReminderEventPort`); nur Adapter beziehungsweise `KeycloakProfileService` importieren Keycloak- oder Web-Push-Typen.

**Begründung (laut früherem ADR):** Testbarkeit an den Schichtgrenzen (Mocks), nachvollziehbarer Datenfluss, zentrale Autorisierung in den Services ([AE-051](AE-051-berechtigung-ueber-teilnehmerrolle-am-termin.md)), Events als Entkopplung ([AE-048](AE-048-domaenenereignisse-ueber-cdi-events.md)).

**Alternativen (laut ADR):** Dreischicht-Modell, hexagonale Architektur, vertikale Feature-Schnitte.

**Konsequenzen:** Viel Boilerplate (DTO, Mapper, Repository je Konzept). Die Schichtung wird nicht automatisch erzwungen (kein ArchUnit); `AppointmentsResource` greift zum Beispiel direkt auf `AppointmentRepository` zu.
