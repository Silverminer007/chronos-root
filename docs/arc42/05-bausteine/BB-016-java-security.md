---
id: BB-016
title: "Java-Backend: Security-Paket (Ebene 2)"
status: entwurf
typ: baustein-ebene-2
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/security
  - backend/src/main/resources/application.properties
verknuepft: [KZ-001, KZ-002, KZ-007, KZ-012]
---

# Java-Backend: Security-Paket (Ebene 2)

| Klasse | Aufgabe |
|---|---|
| `PrincipalContext` | `@RequestScoped`-Bean mit `principal` (`UserIdentity`) und `adminRequest` (Standard `false`) |
| `PrincipalContextFilter` | JAX-RS-Request-Filter, Priorität `AUTHORIZATION + 1`: baut aus den JWT-Claims `sub`, `given_name`, `family_name`, `email`, `picture` die `UserIdentity`, schreibt sie in den `PrincipalContext`, schreibt sie als Profil-Cache fort (`IdentityPort.upsert`, Fehler werden nur geloggt) und setzt `adminRequest` für Pfade unter `/api/v2/admin/` |

Ohne Subject im Token kehrt der Filter zurück und lässt den Principal ungesetzt. Die Token-Validierung selbst übernimmt `quarkus-oidc` (Konfiguration `quarkus.oidc.*`, [KT-003](../03-kontext/KT-003-keycloak.md)); Rollen werden aus `realm_access/roles` gelesen. Konzept: [KZ-001](../08-konzepte/KZ-001-principal-kontext.md), Berechtigungen: [KZ-002](../08-konzepte/KZ-002-autorisierung.md), Token-Führung: [KZ-007](../08-konzepte/KZ-007-sicherheit-und-tokens.md).
