---
id: KZ-012
title: Identität und Profil-Cache
status: entwurf
typ: konzept
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/LocalDbIdentityAdapter.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/ports/IdentityPort.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/UserIdentity.java
  - backend/src/main/resources/db/migration/V2.1.0__create_user_profiles.sql
verknuepft: [LS-003, KT-003, BB-016, BB-008, LZ-008]
---

# Identität und Profil-Cache

Benutzerdaten gehören Keycloak. Das Backend speichert nur die `oidcId` an Fachdaten und führt in `user_profiles` einen Cache (`oidc_id`, Vor-/Nachname, E-Mail, Bild-URL, `updated_at`), den der `IdentityPort` kapselt; kein Fachcode kennt Keycloak-Typen (Ausnahme: `KeycloakProfileService` für Schreibaktionen).

- **Schreiben:** `PrincipalContextFilter` schreibt bei jeder authentifizierten Anfrage die JWT-Claims per `INSERT … ON CONFLICT DO UPDATE` fort (eigene Transaktion, Fehler brechen die Anfrage nicht ab).
- **Lesen:** `findById`/`findByIds` lesen den Cache (eine `IN`-Abfrage); fehlende Einträge holt der Adapter aus Keycloak und speichert sie. Ein in Keycloak gelöschter Benutzer wird als Sentinel (`UserIdentity.deleted`, Anzeigename „Gelöschter Benutzer“) zurückgegeben und nicht gespeichert.
- **Suchen:** `search` fragt immer Keycloak (vollständig) und cacht die Treffer als Nebeneffekt; Grundlage für Freundesvorschläge und das Auflösen per E-Mail ([LZ-008](../06-laufzeit/LZ-008-freundschaftsanfrage.md)).
- **Aktualität:** Profiländerungen in Keycloak erscheinen für andere erst mit der nächsten authentifizierten Anfrage des betroffenen Benutzers; es gibt keine Invalidierung und kein Ablaufdatum des Caches ([R-009](../11-risiken/R-009-profil-upsert-bei-jeder-anfrage.md)).
- **Ausfall:** Bei Keycloak-Fehlern für nicht gecachte Benutzer wirft der Adapter `ServiceUnavailableException`.
