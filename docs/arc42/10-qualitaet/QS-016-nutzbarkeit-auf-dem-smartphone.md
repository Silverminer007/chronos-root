---
id: QS-016
title: "Nutzbarkeit und Installierbarkeit auf dem Smartphone"
status: entwurf
typ: qualitaetsszenario
quellen:
  - frontend/playwright.config.ts
  - frontend/e2e/public-pages.spec.ts
  - frontend/public/manifest.webmanifest
  - frontend/code-style.md
verknuepft: [QA-011]
---

# QS-016: Nutzbarkeit und Installierbarkeit auf dem Smartphone

| | |
|---|---|
| **Reiz** | Eine Person ruft die App auf einem Smartphone auf oder installiert sie über den Browser („Zum Startbildschirm“, Anleitung auf `/public/install`). |
| **Antwort** | Das Layout ist mobile-first (einspaltig, ab `sm:` 640 px und `lg:` 1024 px zusätzliche Spalten) und bietet helle und dunkle Darstellung; `manifest.webmanifest` liefert Name, Icons (192/512 px, maskable und any), Screenshots (schmal und breit) und Farben für die Installation. |
| **Messung** | Die öffentlichen Seiten und die Weiterleitungen laufen in der E2E-Suite auch im Playwright-Projekt `mobile-chrome` (Gerät Pixel 5) fehlerfrei; der Manifest-Test prüft Content-Type, `name`, `icons` und `start_url`. Mindestbreiten, Zielgeräte und Touch-Zielgrößen sind nicht festgelegt **(?)**. |
| **Nachweis** | `frontend-ci.yml` (E2E mit beiden Projekten); angemeldete Ansichten (Termine, Gruppen, Einstellungen) sind mobil nicht automatisch getestet. |
