---
id: AE-049
title: "Lokaler Profil-Cache hinter IdentityPort"
status: entwurf
typ: architekturentscheidung
datum: 2026-07-16
quellen:
  - backend/src/main/resources/db/migration/V2.0.0__remove_users_table.sql
  - backend/src/main/resources/db/migration/V2.1.0__create_user_profiles.sql
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/ports/IdentityPort.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/LocalDbIdentityAdapter.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/security/PrincipalContextFilter.java
verknuepft: [AE-003, AE-010, G-048, G-050, R-004, R-009]
---

# AE-049: Lokaler Profil-Cache hinter IdentityPort

**Entscheidung:** Keycloak ist die einzige Quelle der Benutzeridentität. Die frühere Tabelle `users` entfiel (V2.0.0); alle Verweise auf Benutzer sind Strings mit der Keycloak-`oidcId` (`*_oidcid`). Namen, E-Mail und Bild liest die Anwendung aus der Cache-Tabelle `user_profiles` (V2.1.0) über den `IdentityPort`:
- `PrincipalContextFilter` schreibt bei **jeder** authentifizierten Anfrage die JWT-Claims (`given_name`, `family_name`, `email`, `picture`) per Upsert in den Cache (`REQUIRES_NEW`; ein Fehler bricht die Anfrage nicht ab).
- `findById`/`findByIds` lesen den Cache, laden Fehlendes aus Keycloak nach und speichern es; ein in Keycloak gelöschter Benutzer wird zum Platzhalter „Gelöschter Benutzer" (nicht persistiert).
- `search` (Freundesvorschläge, Admin) fragt Keycloak, damit auch noch nie angemeldete Benutzer auffindbar sind, und cacht die Treffer.

**Begründung (laut Migrationskommentar):** Keine zweite, auseinanderlaufende Benutzerverwaltung und keine Keycloak-Admin-API-Aufrufe zur Laufzeit für Teilnehmerlisten und Nachrichten. Der Port hält Keycloak-Typen aus der Domäne ([AE-010](AE-010-geschichtetes-backend-mit-ports.md)).

**Alternativen:** Eigene `users`-Tabelle pflegen (bis V1.x, abgelöst); jede Profilanzeige direkt aus Keycloak lesen (langsam, Ausfallabhängigkeit).

**Konsequenzen:** Profildaten anderer Benutzer sind nur so aktuell wie deren letzte Anfrage; jede Anfrage verursacht einen Schreibzugriff ([R-009](../11-risiken/R-009-profil-upsert-bei-jeder-anfrage.md)); Suche und Profiländerung brauchen weiterhin Keycloak ([R-004](../11-risiken/R-004-keycloak-als-single-point-of-failure.md)).
