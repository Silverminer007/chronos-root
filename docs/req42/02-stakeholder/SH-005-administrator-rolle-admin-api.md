---
id: SH-005
title: Administrator (Rolle ADMIN_API)
status: entwurf
typ: stakeholder
quellen: [backend/src/main/java/de/chronos_live/admin/presentation/, frontend/app/pages/admin/]
verknuepft: []
---

# SH-005: Administrator (Rolle ADMIN_API)

Betreut Chronos über die Admin-Oberfläche und die Admin-API (Rolle `ADMIN_API` im Keycloak): Benutzer, Termine, Gruppen, Freundschaften, Statistiken und Push-Versand samt Protokoll.

**Einfluss:** hoch auf die Daten: Administratoren umgehen alle Teilnehmer-, Besitzer- und Freundschaftsprüfungen ([FA-007](../04-product-backlog/anforderungen/FA-007-admin-api.md)). Personen: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).

**Erwartung:** Werkzeuge zur Betreuung und Fehlersuche, ohne in die Datenbank zu greifen (abgeleitet aus dem Funktionsumfang).

**Hilft bei der Analyse:** kennt Statistik und Push-Protokoll, also die Datenbasis der Metriken in den Zielen.
