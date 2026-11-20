---
id: SH-003
title: Teilnehmer (Rollen ATTENDANT, GUEST)
status: entwurf
typ: stakeholder
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/domain/UserRole.java, frontend/app/pages/index.vue]
verknuepft: []
---

# SH-003: Teilnehmer (Rollen ATTENDANT, GUEST)

Eingeladene Personen sagen zu oder ab, lesen und schreiben Terminnachrichten, pflegen Freundschaften und Gruppen und stellen ihre Benachrichtigungen ein. `GUEST` ist die niedrigste Teilnahmerolle.

**Einfluss:** Anwenderkreis ohne Entscheidungsrecht; wirkt über Rückmeldungen und die Nutzerumfrage ([FA-015](../04-product-backlog/anforderungen/FA-015-nutzerumfrage-beantworten.md)). Größe und Namen: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).

**Erwartung:** Einfache Zu- und Absage, verständliche Benachrichtigungen, deutsche Oberfläche ([RB-001](../07-randbedingungen/RB-001-alle-ui-texte-sind-deutsch.md)), Nutzung auf dem Smartphone ([QA-011](../06-qualitaet/QA-011-bedienbarkeit-auf-dem-smartphone.md)).

**Hilft bei der Analyse:** Quelle für Bedienbarkeit und Benachrichtigungswünsche. **Hindert:** Wer Push nicht erlaubt oder nicht installieren kann, ist nicht erreichbar ([RA-002](../12-risiken-annahmen/RA-002-nutzer-haben-push-faehigen-browser.md)).
