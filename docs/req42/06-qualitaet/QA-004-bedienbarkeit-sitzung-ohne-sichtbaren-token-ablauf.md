---
id: QA-004
title: Bedienbarkeit: Sitzung ohne sichtbaren Token-Ablauf
status: entwurf
typ: qualitaetsanforderung
quellen: [frontend/public/push-sw.js, frontend/app/plugins/auth.client.ts]
verknuepft: [FA-013]
---

# QA-004: Bedienbarkeit: Sitzung ohne sichtbaren Token-Ablauf

**Qualitätsmerkmal (ISO 25010):** Benutzbarkeit (Fehlertoleranz)

## Szenario
Läuft das Zugriffstoken während der Nutzung ab, merkt die Person nichts: keine Fehlermeldung `401`, kein erneutes Anmelden, solange ihre Sitzung (Refresh-Token) gültig ist. Ist die Sitzung abgelaufen, führt die App zur Landingpage.

## Messbar durch
Kein automatisierter Test, manuelle Prüfung. Ablauf: Der Service Worker erneuert das Zugriffstoken und meldet bei abgelaufener Sitzung `SESSION_EXPIRED`, worauf die App zur Landingpage führt. Zielwert für die Sitzungsdauer: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).
