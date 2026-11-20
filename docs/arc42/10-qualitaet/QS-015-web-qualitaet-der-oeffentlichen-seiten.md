---
id: QS-015
title: "Web-Qualität der öffentlichen Seiten"
status: entwurf
typ: qualitaetsszenario
quellen:
  - frontend/.lighthouserc.json
  - frontend/package.json
  - .github/workflows/frontend-ci.yml
verknuepft: [QA-010]
---

# QS-015: Web-Qualität der öffentlichen Seiten

| | |
|---|---|
| **Reiz** | Ein Lighthouse-CI-Lauf (`startServerCommand`: `node .output/server/index.mjs`, 2 Läufe je Seite) prüft `/`, `/public/impressum` und `/public/install` des gebauten Frontends. |
| **Antwort** | Die Läufe bewerten Zugänglichkeit und SEO als Fehler (Abbruch), Performance, Best Practices und PWA als Warnung. |
| **Messung** | Zugänglichkeit >= 0,85 und SEO >= 0,9 (`error`); Performance >= 0,7, Best Practices >= 0,85, PWA >= 0,6 (`warn`). |
| **Nachweis** | Nur die Konfiguration existiert. Weder `frontend-ci.yml` noch ein npm-Skript führt Lighthouse aus; die Werte sind nicht überwacht **(?)** (Lücke gegenüber [QA-010](../../req42/06-qualitaet/QA-010-web-qualitaet-der-oeffentlichen-seiten.md)). |
