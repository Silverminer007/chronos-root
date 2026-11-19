---
id: FA-009
title: Terminliste paginiert abrufen
status: entwurf
typ: anforderung
quellen: [kb/adr/ADR-2026-10-20-026-appointment-list-and-embedded-details.md]
verknuepft: [F-002]
---

# FA-009: Terminliste paginiert abrufen

Die Terminliste wird seitenweise geliefert und enthält Teilnehmer, Nachrichten und Gruppenteilnahmen eingebettet.

## Akzeptanzkriterien
- Antwort als `PagedResponse`.
- Eingebettete Daten vermeiden Folgeaufrufe.

## Features
- F-002

## Priorität
**(?)** vom Product Owner festzulegen.
