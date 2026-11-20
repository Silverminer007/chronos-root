---
id: KZ-011
title: Deutsche Oberfläche
status: entwurf
typ: konzept
quellen:
  - frontend/app
  - frontend/public/push-sw.js
  - frontend/e2e
  - frontend/package.json
verknuepft: [RB-001, KZ-005]
---

# Deutsche Oberfläche

Alle nutzersichtbaren Texte sind fest im Code deutsch (Komponenten, Store-Fallbacktexte, Push-Texte im Service Worker, Systemnachrichten im Backend wie „… hat zugesagt“); es gibt keine i18n-Bibliothek und keine Textkataloge. E2E-Tests prüfen deutsche Texte. Code, Typen, API-Pfade und JSON-Felder bleiben englisch ([Glossar](../12-glossar/README.md)). Datums- und Zeitformate erzeugt Luxon (`useDateFormatter`) mit `toFormat` ohne festes Locale; Wochentags- und Datumsformat folgen daher der Browsersprache, nur Einzelstellen setzen `de` bzw. `de-DE`. Ausnahme von der Regel sind englische Backend-Fehlermeldungen ([KZ-005](KZ-005-fehlerbehandlung.md)).
