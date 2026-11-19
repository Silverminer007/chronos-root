---
id: F-016
title: Nutzerumfrage
status: entwurf
typ: feature
quellen: [frontend/app/pages/survey/[surveyId].vue]
verknuepft: [Z-006, SH-001, SH-003, FA-015]
---

# F-016: Nutzerumfrage

Kurze Umfrage mit Einzel-, Freitext- und Mehrfachauswahl; Teilnahme wird per Eligibility-Prüfung begrenzt, Aufruf auch aus Push möglich.

## Umsetzung (Ist)
Seite `survey/[surveyId].vue`, `GET /api/v2/survey/{id}/eligible`. **(?)** Backend-Endpunkt im Java-/Rust-Backend nicht gefunden, Ablage der Antworten klären.

## Ziele
- Z-006

## Stakeholder
- SH-001
- SH-003

## Anforderungen
- FA-015
