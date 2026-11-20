---
id: Z-003
title: Gruppen und Freundeskreise abbilden
status: entwurf
typ: ziel
quellen: [docs/vision/product-vision.md, backend/src/main/java/de/chronos_live/chronos_date_api/application/GroupService.java]
verknuepft: [SH-002, SH-003]
---

# Z-003: Gruppen und Freundeskreise abbilden

**Ziel:** Personen über Freundschaften und Gruppen verwalten und ganze Gruppen gebündelt einladen.

**Vorteil:** Weniger Aufwand beim Einladen wiederkehrender Personenkreise.

**Metrik:** Anteil der Termine, zu denen mindestens eine Gruppe eingeladen wurde, sowie Zahl der Gruppen und Mitglieder je Benutzer. Datenbasis: Gruppen und Mitglieder in der Statistik der Administration ([FA-007](../04-product-backlog/anforderungen/FA-007-admin-api.md)), Gruppenteilnahmen in der Datenbank ([M-002](../05-modelle/M-002-fachdatenmodell.md)). Zielwert und Zeitraum: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).
