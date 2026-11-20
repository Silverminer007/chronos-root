---
id: Z-002
title: Rückmeldungen ohne Nachfragen erhalten
status: entwurf
typ: ziel
quellen: [docs/vision/product-vision.md, backend/src/main/java/de/chronos_live/chronos_date_api/application/reminder/]
verknuepft: [SH-002, SH-003, SH-004]
---

# Z-002: Rückmeldungen ohne Nachfragen erhalten

**Ziel:** Einladungen, Änderungen und Erinnerungen erreichen die Beteiligten aktiv per Push; Zu- und Absagen werden eingeholt.

**Vorteil:** Verantwortliche müssen nicht nachtelefonieren, Teilnehmer verpassen keine Änderung.

**Metrik:** Anteil der Benutzer mit mindestens einem Push-Abo und Anteil erfolgreich zugestellter Benachrichtigungen. Datenbasis: Push-Abos in der Statistik und Push-Protokoll der Administration ([FA-004](../04-product-backlog/anforderungen/FA-004-push-benachrichtigungen.md), [FA-007](../04-product-backlog/anforderungen/FA-007-admin-api.md)). Zielwert und Zeitraum: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md). Einschränkungen des Ziels: [RA-002](../12-risiken-annahmen/RA-002-nutzer-haben-push-faehigen-browser.md), [RA-012](../12-risiken-annahmen/RA-012-standardeinstellungen-unterdruecken-einladungsbenachrichtigung.md).
