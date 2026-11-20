---
id: KZ-007
title: Sicherheit und Tokenführung
status: entwurf
typ: konzept
quellen:
  - frontend/server/api/auth
  - frontend/server/api/v2/[...path].ts
  - backend/src/main/resources/application.properties
  - backend/src/main/java/de/chronos_live/chronos_date_api/security/PrincipalContextFilter.java
  - scripts/check-no-local-settings.sh
verknuepft: [RB-002, AE-013, RB-011, LS-002, LS-003, LS-004, QA-009]
---

# Sicherheit und Tokenführung

- **Tokens:** `kc_access` und `kc_refresh` liegen nur in httpOnly-Cookies (`secure`, `sameSite=none`); der Nuxt-Server setzt den Bearer-Header ([LS-002](../04-loesungsstrategie/LS-002-server-routen-als-proxy.md)). Lesbar ist nur `kc_expires` (Ablaufzeit).
- **Validierung:** `quarkus-oidc` prüft Signatur und Ablauf; die Audience wird nicht geprüft (`quarkus.oidc.token.audience` nicht gesetzt, [R-005](../11-risiken/R-005-token-audience-wird-nicht-geprueft.md)). Rollen aus `realm_access/roles`.
- **Autorisierung:** [KZ-002](KZ-002-autorisierung.md). Ungültiges Token ⇒ 401, fehlende Berechtigung ⇒ 403.
- **Authentifizierungspflicht:** Es gibt keine explizite Pflicht (kein `@Authenticated`, keine `quarkus.http.auth.permission`); Ressourcen sind `@PermitAll` oder unannotiert. Bei fehlendem Token bleibt der `PrincipalContext` leer, `getPrincipal().oidcId()` wirft eine `NullPointerException` und die Antwort ist 500 statt 401. In der Praxis erreicht nur der Nuxt-Proxy das Backend ([KT-002](../03-kontext/KT-002-technischer-kontext.md)).
- **CSRF:** Die Cookies haben `sameSite=none`; weder Proxy noch Nuxt-Konfiguration enthalten CSRF-Schutz ([R-008](../11-risiken/R-008-proxy-ohne-csrf-schutz.md)). 
- **Geheimnisse:** nie im Repository ([RA-009](../02-randbedingungen/RA-009-geheimnisse-ueber-austauschbare-provider.md)); `repo-hygiene` verhindert versionierte lokale Agent-Einstellungen und LiteLLM-Header (RB-011); ein in der Historie geleakter Schlüssel muss rotiert werden (R-001). Frontend: gitleaks in der CI ([RA-004](../02-randbedingungen/RA-004-qualitaets-gates-frontend.md)).
