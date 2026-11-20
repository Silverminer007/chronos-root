---
id: KZ-001
title: Principal-Kontext
status: entwurf
typ: konzept
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/security
  - backend/src/main/java/de/chronos_live/chronos_date_api/presentation
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java
verknuepft: [BB-016, KZ-002, KZ-012, LS-003]
---

# Principal-Kontext

Die Identität des Aufrufers wird einmal je Anfrage im `PrincipalContextFilter` aus dem JWT gelesen und im request-scoped `PrincipalContext` abgelegt ([BB-016](../05-bausteine/BB-016-java-security.md)). Regeln:

- Nur die Presentation-Schicht liest den Principal; Ressourcen reichen die `oidcId` als explizit benannten Parameter (`actingUserOidcId`, `requestingUserOidcId`) an Services weiter. Services bleiben dadurch ohne Request-Kontext aufrufbar, etwa aus Beobachtern und Scheduler (dort gibt es keinen Principal).
- Die einzige Ausnahme in der Application-Schicht ist `AuthorizationService`, der `PrincipalContext.adminRequest` liest, um Admin-Anfragen von den Prüfungen auszunehmen ([KZ-002](KZ-002-autorisierung.md)).
- Benutzer werden ausschließlich über die `oidcId` (Keycloak-Subject) identifiziert, nie über eine lokale ID ([KZ-012](KZ-012-identitaet-und-profil-cache.md)).
