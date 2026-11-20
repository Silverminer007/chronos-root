---
id: M-002
title: Fachdatenmodell
status: entwurf
typ: modell
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/domain/]
verknuepft: [FA-001, FA-002, FA-003, FA-004, FA-006]
---

# M-002: Fachdatenmodell

```mermaid
erDiagram
  PERSON ||--o{ TEILNAHME : hat
  TERMIN ||--o{ TEILNAHME : hat
  TERMIN ||--o{ NACHRICHT : enthaelt
  TERMIN ||--o{ GRUPPENTEILNAHME : laedt_ein
  GRUPPE ||--o{ GRUPPENTEILNAHME : wird_eingeladen
  GRUPPE ||--o{ MITGLIEDSCHAFT : umfasst
  PERSON ||--o{ MITGLIEDSCHAFT : ist
  PERSON ||--o{ FREUNDSCHAFTSANFRAGE : sendet_empfaengt
  PERSON ||--o{ GERAETEABO : besitzt
  PERSON ||--o{ PUSHPROTOKOLL : empfaengt
  PERSON ||--|| EINSTELLUNGEN : hat
```

| Begriff | Fachliche Eigenschaften |
|---|---|
| Person | Identität, Name, E-Mail, Bild liegen bei Keycloak; Chronos hält nur eine Kopie ([RM-010](../11-roadmaps/RM-010-nutzerdaten-nur-in-keycloak.md)) |
| Termin | Name, Beschreibung, Ort, Start, Ende, Mindestteilnehmerzahl, Status ([M-003](M-003-statusautomat-des-termins.md)) |
| Teilnahme | Person an Termin mit Rolle ([M-006](M-006-rollenmatrix.md)) und Status ([M-004](M-004-statusautomat-der-teilnahme.md)); optional Verweis auf die Gruppe, über die sie entstand |
| Gruppenteilnahme | Gruppe an Termin mit Rolle für ihre Mitglieder |
| Gruppe | Name, Besitzer; Mitgliedschaften |
| Freundschaftsanfrage | Absender, Empfänger, Status ([M-005](M-005-statusautomat-der-freundschaft.md)), Zeitpunkte |
| Nachricht | Text, Absender, Zeitpunkt; auch vom System erzeugt |
| Geräteabo | Push-Abo eines Geräts |
| Push-Protokoll | Zustellversuch mit Person, Ereignistyp, Ergebnis (Erfolg, HTTP-Status), Fehlertext und Zeitpunkt ([FA-004](../04-product-backlog/anforderungen/FA-004-push-benachrichtigungen.md)) |
| Einstellungen | Benachrichtigungsstufen je Ereignisart ([M-007](M-007-benachrichtigungsmatrix.md)) |

Begriffe: [Glossar](../../arc42/12-glossar/README.md). Technische Umsetzung: arc42, [Kapitel 8](../../arc42/08-konzepte/README.md).
