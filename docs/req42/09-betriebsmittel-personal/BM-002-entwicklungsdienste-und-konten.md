---
id: BM-002
title: Entwicklungsdienste und Konten
status: entwurf
typ: betriebsmittel
quellen: [.github/workflows/, .github/CODEOWNERS, frontend/nuxt.config.ts, wiki/proposals/]
verknuepft: [SH-007]
---

# BM-002: Entwicklungsdienste und Konten

Ohne Zugangsdaten:

| Dienst | Zweck | Bemerkung |
|---|---|---|
| GitHub (Organisation/Konto `Silverminer007`) | Quellcode, Tickets, Actions-CI, Image-Registry, Releases (zuletzt v1.10.0 am 2026-10-03) | öffentliches Repository |
| Keycloak-Realm `chronos` | Identitäten der Nutzer | nicht Teil des Deployments ([BM-001](BM-001-laufzeitinfrastruktur.md)); Betreiber: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md) |
| Sentry | Fehlerberichte | nur Konfiguration (DSN, Auth-Token) vorhanden, keine Anbindung im Code ([SC-001](../03-scope/SC-001-systemkontext-akteure-und-nachbarsysteme.md)) |
| Figma | Entwurf des geplanten Angular-Frontends | siehe [RM-001](../11-roadmaps/RM-001-angular-frontend-nach-figma.md) |
| OpenProject | früheres Ticketsystem, Tickets nach GitHub übernommen | Fortbestand: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md) |

Lizenzen: Im Repository liegt keine Lizenzdatei (GitHub zeigt keine Lizenz); Lizenzen der Abhängigkeiten sind nicht erfasst ([RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md)).
