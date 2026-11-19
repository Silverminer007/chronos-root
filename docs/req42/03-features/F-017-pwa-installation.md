---
id: F-017
title: PWA-Installation
status: entwurf
typ: feature
quellen: [frontend/app/pages/public/install.vue, frontend/app/components/PwaInstallDialog.vue, kb/adr/ADR-2025-09-28-005-service-worker.md]
verknuepft: [Z-002, SH-003, FA-016]
---

# F-017: PWA-Installation

Chronos ist als Progressive Web App installierbar; Hinweisdialog und Installationsseite erklären das Vorgehen.

## Umsetzung (Ist)
`PwaInstallDialog.vue`, Seite `public/install.vue`, Service Worker `push-sw.js`.

## Ziele
- Z-002

## Stakeholder
- SH-003

## Anforderungen
- FA-016
