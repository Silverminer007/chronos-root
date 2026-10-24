# ADR-036: Java `AppointmentParticipationResource` bekommt wieder ein Klassen-`@Path`

- Status: akzeptiert (Ticket #162, Befund 1)

## Kontext
Seit #54 (`d6ba7ae`) deklarierte `AppointmentParticipationResource` nur absolute Methoden-Pfade
(`/api/v2/appointments/{id}/participants/...`) und kein Klassen-`@Path`. RESTEasy Reactive wählt die Ressourcenklasse
über das Klassen-Präfix; `AppointmentsResource` (`/api/v2/appointments`) gewann, und alle 8 Teilnahme-Routen
antworteten mit 404 `Unable to find matching target resource method`. Die Unit-Tests rufen die Methoden direkt auf und
haben den Defekt nicht gesehen.

## Entscheidung
- Klassen-`@Path("/api/v2/appointments")` und relative Methoden-Pfade (`/{id}/participation` …) – exakt der im
  Ticket dokumentierte Diagnose-Patch. Die öffentlichen URLs ändern sich nicht.
- Abgesichert durch `ParticipationRoutesIntegrationTest` (HTTP-Ebene, alle 8 Routen: weder 404 noch 405). Dafür ist
  `io.rest-assured:rest-assured` als Test-Abhängigkeit deklariert (von der Quarkus-BOM verwaltet); `quarkus-junit` bringt
  sie nicht mehr mit.
- Nicht Teil dieser Änderung: Befund 2 (500 statt 4xx bei ungültigen Eingaben) und 3 (`unlink` unbekannter Provider).
  Rust liefert dort weiter 4xx, die Abweichungen bleiben mit `java-defect (#162)` in `contract-tests/allowlist.json`.
  Das Ticket bleibt dafür offen.

## Bekannte Altlast (nicht behoben)
Der übrige Java-Testbaum kompiliert auf diesem Stand nicht (`BaseIntegrationTest`, `FriendshipIntegrationTest`,
`GroupIntegrationTest` verweisen auf entfernte Klassen `User`, `UserRepository`, `Friendship`, `GroupMembership…`;
`AppointmentParticipationResourceTest` auf `UserRole.ORGANIZER`), und `./mvnw verify` scheitert an 5 Checkstyle-Fehlern
in fremden Dateien. Der neue Test wurde deshalb mit temporär entfernten Altdateien und `-Dcheckstyle.skip` ausgeführt.
