---
id: G-005
title: "Zusage / Absage"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/ParticipationStatus.java
verknuepft: [G-003]
---

# G-005: Zusage / Absage

**Code-Name:** `ParticipationStatus`: `APPROVED` (Zusage), `REJECTED` (Absage), `PENDING` (ausstehend)

Antwort eines [Teilnehmers](G-003-teilnehmer.md) auf die Einladung; jeder hat zunächst `PENDING`. Zu- oder Absage erzeugt eine [Systemnachricht](G-011-nachricht-und-systemnachricht.md).
