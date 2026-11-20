---
id: FA-016
title: Installation als PWA
status: entwurf
typ: anforderung
quellen: [frontend/public/manifest.webmanifest, frontend/app/pages/public/install.vue, frontend/app/components/PwaInstallDialog.vue, frontend/e2e/public-pages.spec.ts]
verknuepft: [F-017]
---

# FA-016: Installation als PWA

*Als Teilnehmer möchte ich Chronos installieren können und erklärt bekommen, wie, damit ich es wie eine App nutze.*

## Akzeptanzkriterien
- Ein Web-App-Manifest (Name „Chronos“, eigenständige Anzeige, Icons, Startseite `/`) wird mit passendem Inhaltstyp ausgeliefert (E2E-Test).
- Die Installationsseite `/public/install` ist ohne Anmeldung erreichbar und erklärt die Installation für Safari (iOS, macOS ab 17), Chrome und Edge (Desktop, Android) und Firefox (mit Hinweis auf eingeschränkte Unterstützung); auf iOS weist sie darauf hin, dass nur Safari installieren kann.
- Auf iOS ist die Installation Voraussetzung für Push ([RA-002](../../12-risiken-annahmen/RA-002-nutzer-haben-push-faehigen-browser.md)).
- Bietet der Browser die Installation an, erscheint ein Dialog „App installieren“ mit „Jetzt installieren“ und „Später“.
