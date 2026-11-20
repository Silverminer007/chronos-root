---
id: G-003
title: "Teilnehmer"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/AppointmentParticipation.java
  - frontend/app/types/index.ts
verknuepft: [G-001, G-004, G-005, G-008]
---

# G-003: Teilnehmer

**Code-Name:** `AppointmentParticipation` (Backend, Tabelle `appointment_participation`); `UserParticipant` (Frontend, API-Feld `participants`)

Beteiligung eines Benutzers (`userOidcId`, siehe [oidcId](G-048-oidcid.md)) an einem Termin mit [Rolle](G-004-rolle-teilnehmer.md) und [Zusage-Status](G-005-zusage-absage.md). Eine neue Teilnahme mit Status `PENDING` ist die **Einladung**. Stammt sie aus einer Gruppe, enthält `groupParticipationId` die ID der [Gruppe](G-006-gruppe.md) (nicht der Gruppenteilnahme), über die er eingeladen wurde ([Gruppenteilnahme](G-008-gruppenteilnahme.md), [AE-055](../09-entscheidungen/AE-055-gruppeneinladung-als-abgeleitete-einzelteilnahmen.md)). Wer einen Termin anlegt, wird automatisch Teilnehmer mit Rolle `RESPONSIBLE`.
