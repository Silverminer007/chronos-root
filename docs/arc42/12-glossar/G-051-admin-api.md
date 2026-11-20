---
id: G-051
title: "Admin-API"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/admin/presentation/
verknuepft: [AE-051, QS-007, FA-007]
---

# G-051: Admin-API

**Code-Name:** `/api/v2/admin/*`, Paket `de.chronos_live.admin`, Realm-Rolle `ADMIN_API`

Verwaltungsschnittstelle für Termine, Gruppen, Freundschaften, Benutzer, Push und Statistik (Frontend: Seiten `admin/index`, `admin/push`). Nur mit Rolle `ADMIN_API` erreichbar; Anfragen umgehen die Termin-Rollenprüfung ([AE-051](../09-entscheidungen/AE-051-berechtigung-ueber-teilnehmerrolle-am-termin.md)).
