---
id: KZ-004
title: DTO-Mapping und Validierung
status: entwurf
typ: konzept
quellen:
  - kb/patterns/001-mapstruct-dto-conversion.md
  - kb/patterns/002-entity-validation.md
  - backend/.../mapper
verknuepft: [BB-005]
---

# DTO-Mapping und Validierung

Entitäten verlassen die API nie direkt; MapStruct-Mapper (`AppointmentMapper`, `GroupMapper`, …) konvertieren Entität ↔ DTO. Listen sind seitenweise (`PagedResponse<T>`). Eingaben werden validiert (`ValidationException`), Domänenfehler als `BusinessException`-Hierarchie.
