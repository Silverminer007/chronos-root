---
id: F-009
title: Terminnachrichten
status: entwurf
typ: feature
quellen: [backend/.../presentation/MessageResource.java, kb/adr/ADR-2026-10-20-027-system-messages-and-group-participants.md]
verknuepft: [Z-001, SH-003, SH-002, FA-005]
---

# F-009: Terminnachrichten

Teilnehmer lesen und schreiben Nachrichten im Kontext eines Termins; das System erzeugt Systemnachrichten (z. B. Verschiebung, Zu-/Absage).

## Umsetzung (Ist)
`GET/POST /appointments/{id}/messages`.

## Ziele
- Z-001

## Stakeholder
- SH-003
- SH-002

## Anforderungen
- FA-005
