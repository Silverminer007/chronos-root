---
id: KZ-005
title: Fehlerbehandlung
status: entwurf
typ: konzept
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/exception
  - backend/src/main/java/de/chronos_live/chronos_date_api/dto/ErrorResponse.java
  - frontend/app/utils/errors.ts
verknuepft: [KZ-011, BB-014, RB-001]
---

# Fehlerbehandlung

**Backend:** Fachfehler erben von `BusinessException` (mit `errorCode`): `BadRequestException` (400, `BAD_REQUEST`), `ValidationException` (400, `VALIDATION_ERROR`, optional `fieldErrors`), `ForbiddenException` (403, `FORBIDDEN`), `ResourceNotFoundException` (404, `RESOURCE_NOT_FOUND`). `ExceptionMapper`s (dazu `NotFoundException` und ein Auffangmapper für alle übrigen Ausnahmen, 500) liefern einheitlich `ErrorResponse` (`timestamp`, `status`, `error`, `errorCode`, `message`, `path`, `fieldErrors`). `@RolesAllowed`-Verweigerung bei Admin-Endpunkten liefert die leere 403 von Quarkus.

**Frontend:** `getErrorMessage(err, fallback)` zeigt `data.message` der Backend-Antwort, sonst `message`, sonst den deutschen Fallbacktext; `getErrorStatus` liest den HTTP-Status. Die Nuxt-Proxy-Route reicht Backend-Fehler unverändert durch ([BB-002](../05-bausteine/BB-002-frontend-server-routen.md)).

Folge: Die `message` des Backends erreicht die Oberfläche. Sie ist teils deutsch (`ResourceNotFoundException` „… wurde nicht gefunden“, Freundschaftsfehler), teils englisch („You have no role at this appointment“); das Frontend wertet `errorCode` nirgends aus und bildet ihn nicht auf deutsche Texte ab ([KZ-011](KZ-011-deutsche-oberflaeche.md), [TD-009](../11-risiken/TD-009-englische-fehlermeldungen-im-backend.md)).
