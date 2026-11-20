---
id: QA-010
title: Web-Qualität der öffentlichen Seiten
status: entwurf
typ: qualitaetsanforderung
quellen: [frontend/.lighthouserc.json]
verknuepft: [FA-016, FA-017]
---

# QA-010: Web-Qualität der öffentlichen Seiten

**Qualitätsmerkmal (ISO 25010):** Benutzbarkeit (Zugänglichkeit), Effizienz, Kompatibilität

## Szenario
Landingpage, Impressum und Installationsseite erreichen in einem Lighthouse-Lauf mindestens: Zugänglichkeit 0,85 und SEO 0,9 (verbindlich), Performance 0,7, Best Practices 0,85 und PWA 0,6 (Warnung).

## Messbar durch
Lighthouse-CI-Konfiguration `frontend/.lighthouserc.json`. Sie wird weder in der CI noch über ein npm-Skript ausgeführt; die Werte sind daher nicht überwacht ([RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md)).
