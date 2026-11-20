---
id: QA-005
title: Performance unter Last
status: entwurf
typ: qualitaetsanforderung
quellen: [backend/performance-tests/k6/config/options.js, backend/performance-tests/README.md]
verknuepft: []
---

# QA-005: Performance unter Last

**Qualitätsmerkmal (ISO 25010):** Leistungseffizienz (Zeitverhalten)

## Szenario
Bei 50 gleichzeitigen Benutzern über 2 Minuten antwortet das Backend fehlerfrei: Gesamt p95 < 2 s, p99 < 5 s, Fehlerrate < 5 %. Einzelwerte je Endpunkt und Nachweis: [arc42 QS-009](../../arc42/10-qualitaet/QS-009-lastverhalten.md). Einzelwerte (95. Perzentil): Terminliste 1 s, Termin anlegen 1,5 s, Termin abrufen 0,5 s, Gruppenliste 0,8 s, Freundesliste 0,6 s, Benutzer und Einstellungen je 0,3 s. Erwartete reale Nutzerzahlen: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md) und [RA-009](../12-risiken-annahmen/RA-009-nutzungsumfang-und-last-sind-unbekannt.md).
