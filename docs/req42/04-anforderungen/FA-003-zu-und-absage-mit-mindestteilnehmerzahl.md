---
id: FA-003
title: Zu- und Absage mit Mindestteilnehmerzahl
status: entwurf
typ: anforderung
quellen: [backend/.../domain/ParticipationStatus.java]
verknuepft: [F-003, F-005]
---

# FA-003: Zu- und Absage mit Mindestteilnehmerzahl

Teilnehmer können zu- oder absagen; der Terminstatus berücksichtigt die Mindestteilnehmerzahl.

## Akzeptanzkriterien
- Mögliche Antworten: APPROVED, REJECTED, PENDING.
- Unterschreiten der Mindestzahl führt zum Status NOT_ENOUGH_ATTENDEES.
- Der eigene Teilnahmestatus ist abrufbar.

## Features
- F-003
- F-005

## Priorität
**(?)** vom Product Owner festzulegen.
