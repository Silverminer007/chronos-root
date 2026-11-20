---
id: G-010
title: "Erinnerung"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/
  - frontend/public/push-sw.js
verknuepft: [G-013, AE-052, AE-056, TD-008, TD-013]
---

# G-010: Erinnerung

**Code-Name:** `ReminderRule` (Regeln `ShortWeekdayRSVPRule`, `ShortWeekendRSVPRule`, `LongAppointmentRSVPRule`, `AppointmentReminderRule`); Push-Typen `PARTICIPATION_STATUS_PENDING` (Rückmeldung fehlt) und `PARTICIPATION_REMINDER`

Zeitgesteuerte [Push-Nachricht](G-013-push-benachrichtigung.md) zu einem Termin. Rückmelde-Erinnerungen (Rückmeldung fehlt) richten sich nach Dauer und Wochentag (UTC, [AE-056](../09-entscheidungen/AE-056-zeitpunkte-als-utc-instant.md)) des Termins:

| Termin | Zeitpunkte vor Beginn |
|---|---|
| unter 24 h, Beginn Mo bis Do | 7, 4, 2, 1 Tag |
| unter 24 h, Beginn Fr bis So | 4, 2, 1 Woche, danach täglich |
| ab 24 h | 16, 8, 4, 2 Wochen, danach täglich |

Die Terminerinnerung 30 min vorher ist derzeit wirkungslos ([TD-008](../11-risiken/TD-008-30-minuten-erinnerung-ohne-wirkung.md)).
