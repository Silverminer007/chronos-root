---
id: Z-001
title: Termine gemeinsam planen und Teilnahme nachverfolgen
status: entwurf
typ: ziel
quellen: [docs/vision/product-vision.md, backend/src/main/java/de/chronos_live/chronos_date_api/domain/AppointmentStatus.java]
verknuepft: [SH-001, SH-002, SH-003]
---

# Z-001: Termine gemeinsam planen und Teilnahme nachverfolgen

**Ziel:** Termine gemeinsam planen und die Teilnahme nachverfolgen

**Vorteil:** Verantwortliche wissen jederzeit, wer zu- oder abgesagt hat und ob der Termin stattfinden kann, ohne einzeln nachzufragen.

**Metrik:** Anteil der eingeladenen Teilnehmer, die bis zum Beginn der Rückmeldefrist ([M-003](../05-modelle/M-003-statusautomat-des-termins.md)) zu- oder abgesagt haben (Teilnahmestatus ungleich `PENDING`), und Anteil der Termine mit Mindestteilnehmerzahl, die im Zustand `NOT_ENOUGH_ATTENDEES` enden. Datenbasis: Teilnahmen je Status aus der Statistik der Administration ([FA-007](../04-product-backlog/anforderungen/FA-007-admin-api.md)). Zielwert und Zeitraum: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).
