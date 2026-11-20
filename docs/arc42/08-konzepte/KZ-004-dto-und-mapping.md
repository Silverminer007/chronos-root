---
id: KZ-004
title: DTO-Mapping und Validierung
status: entwurf
typ: konzept
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/mapper
  - backend/src/main/java/de/chronos_live/chronos_date_api/dto
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AppointmentService.java
verknuepft: [BB-005, KZ-005]
---

# DTO-Mapping und Validierung

Entitäten verlassen die API nie direkt: MapStruct-Mapper in `mapper/` (`AppointmentMapper`, `GroupMapper`, `MessageMapper`, `SettingsMapper`, `UserMapper`, …) wandeln Entität ↔ DTO. JSON-Felder der DTOs sind überwiegend `snake_case` (`user_role`, `minimal_attendees`, `via_group_id`). Nutzernamen und Profilbilder fügt `AppointmentService.enrichAppointmentDtos` gesammelt aus dem Identitäts-Cache ein (eine Abfrage für alle IDs, [KZ-012](KZ-012-identitaet-und-profil-cache.md)). Listen sind seitenweise (`PagedResponse<T>` mit `data` und `meta{page,size,total}`). Eingaben validieren die Services selbst und werfen `ValidationException` mit optionalen Feldfehlern ([KZ-005](KZ-005-fehlerbehandlung.md)); Bean-Validation-Annotationen werden im Code nicht verwendet, obwohl die Extension `quarkus-hibernate-validator` eingebunden ist.
