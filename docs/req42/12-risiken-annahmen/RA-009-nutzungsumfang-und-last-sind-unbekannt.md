---
id: RA-009
title: Nutzungsumfang und Last sind unbekannt
status: entwurf
typ: annahme
quellen: [deployment/values-prod.yaml]
verknuepft: [QA-005]
---

# RA-009: Nutzungsumfang und Last sind unbekannt

Annahme: Die Nutzerzahl ist klein bis mittel; die Dimensionierung (Produktion zwei Replikate mit Autoskalierung bis 4 bzw. 10, Datenbank 10 Gi, [BM-001](../09-betriebsmittel-personal/BM-001-laufzeitinfrastruktur.md)) genügt. Zielwerte für Nutzerzahl, Antwortzeit und Verfügbarkeit gibt es nicht ([RA-013](RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md)); der Lasttest ([QA-005](../06-qualitaet/QA-005-performance-unter-last.md)) nutzt 50 gleichzeitige Benutzer als Annahme, nicht als erwartete Nutzung.
