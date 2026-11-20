---
id: RA-008
title: Wochentagsregeln gelten in UTC
status: entwurf
typ: annahme
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/utils/AppointmentTimeUtil.java]
verknuepft: [FA-011, M-003]
---

# RA-008: Wochentagsregeln gelten in UTC

Annahme: Ob ein Termin „Mo–Do“ oder „Fr–So“ beginnt, entscheidet sich nach UTC. Termine zwischen Mitternacht und 01:00 Uhr (Winterzeit) bzw. 02:00 Uhr (Sommerzeit) deutscher Ortszeit werden dem Vortag zugeordnet; ein Termin am Montag um 00:30 Uhr zählt dann als Wochenende (Fr–So) und erhält andere Erinnerungs- und Rückmeldefristen ([FA-011](../04-product-backlog/anforderungen/FA-011-automatische-erinnerungen.md), [M-003](../05-modelle/M-003-statusautomat-des-termins.md)). Für übliche Zeiten tagsüber und abends ist die Annahme unkritisch.
