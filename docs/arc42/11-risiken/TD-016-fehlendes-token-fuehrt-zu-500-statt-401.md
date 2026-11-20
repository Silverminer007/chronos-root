---
id: TD-016
title: "Fehlendes Token führt zu 500 statt 401"
status: entwurf
typ: technische-schuld
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/presentation/AppointmentsResource.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/security/PrincipalContextFilter.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/exception/GenericExceptionMapper.java
verknuepft: [AE-003, AE-051, QS-007]
---

# TD-016: Fehlendes Token führt zu 500 statt 401

Die fachlichen Resources (`AppointmentsResource`, `AppointmentParticipationResource`, `GroupsResource`, `MessageResource`, `UserResource`) sind `@PermitAll`. `PrincipalContextFilter` kehrt bei fehlendem `sub` zurück, ohne Fehler; Resources lesen dann `principalContext.getPrincipal().oidcId()` und werfen eine `NullPointerException`, die der `GenericExceptionMapper` als 500 beantwortet. Ein 401 entsteht nur an `@RolesAllowed` der Admin-API ([QS-007](../10-qualitaet/QS-007-zugriffsschutz-der-admin-api.md)). Anonyme Aufrufe sind in Produktion nur über das Netzwerk erreichbar, das den Proxy umgeht (Backend ist `ClusterIP`).

**Auswirkung:** Falscher Statuscode und Fehlerlog-Rauschen bei Anfragen ohne Token (zum Beispiel ein abgelaufenes Token, das der Proxy nicht ersetzt); kein Datenzugriff ohne Identität.

**Maßnahme:** `@Authenticated` an den Resources oder 401 im Filter bei fehlendem Principal.
