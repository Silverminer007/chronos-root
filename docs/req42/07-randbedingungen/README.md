---
title: Randbedingungen
status: entwurf
---

# Randbedingungen

Vorgaben, die die Lösung einschränken. Architekturentscheidungen stehen in arc42 ([Kapitel 9](../../arc42/09-entscheidungen/README.md)), zusätzliche technische Randbedingungen in [Kapitel 2](../../arc42/02-randbedingungen/README.md).

## Organisatorische Randbedingungen

| ID | Titel | Datei |
|---|---|---|
| RB-001 | Alle UI-Texte sind deutsch | [RB-001-alle-ui-texte-sind-deutsch.md](RB-001-alle-ui-texte-sind-deutsch.md) |
| RB-005 | Änderungen an main nur per Pull Request | [RB-005-aenderungen-an-main-nur-per-pull-request.md](RB-005-aenderungen-an-main-nur-per-pull-request.md) |
| RB-008 | Qualitätsprüfungen vor dem Merge | [RB-008-qualitaetspruefungen-vor-dem-merge.md](RB-008-qualitaetspruefungen-vor-dem-merge.md) |
| RB-010 | Zwei Umgebungen: Produktion und Staging | [RB-010-zwei-umgebungen-produktion-und-staging.md](RB-010-zwei-umgebungen-produktion-und-staging.md) |
| RB-011 | Keine Geheimnisse und lokale Agent-Einstellungen im Repository | [RB-011-keine-geheimnisse-und-lokale-agent-einstellungen-im-repository.md](RB-011-keine-geheimnisse-und-lokale-agent-einstellungen-im-repository.md) |

## Technische Randbedingungen

| ID | Titel | Datei |
|---|---|---|
| RB-002 | Identitätsprovider ist Keycloak | [RB-002-identitaetsprovider-ist-keycloak.md](RB-002-identitaetsprovider-ist-keycloak.md) |
| RB-006 | Benachrichtigung nur über Web Push | [RB-006-benachrichtigung-nur-ueber-web-push.md](RB-006-benachrichtigung-nur-ueber-web-push.md) |

## Rechtliche Randbedingungen

| ID | Titel | Datei |
|---|---|---|
| RB-014 | Datenschutz nach DSGVO und Impressumspflicht | [RB-014-datenschutz-nach-dsgvo-und-impressumspflicht.md](RB-014-datenschutz-nach-dsgvo-und-impressumspflicht.md) |

Entfallene IDs (nicht wiederverwenden): RB-003 (Flyway nur vorwärts), RB-004 (Frontend nur über Server-Routen), RB-007 (SPA ohne SSR), RB-013 (Technologiebasis): Architekturentscheidungen bzw. Konzepte, stehen in arc42. RB-009 (Tests) ging in RB-008 auf. RB-012 (geleakter Schlüssel) ist ein Risiko, siehe arc42 R-001.
