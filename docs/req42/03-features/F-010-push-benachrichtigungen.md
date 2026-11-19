---
id: F-010
title: Push-Benachrichtigungen
status: entwurf
typ: feature
quellen: [backend/.../presentation/PushResource.java, backend/.../application/events/, kb/adr/ADR-2026-10-20-019-push-endpoints-and-event-notifications.md]
verknuepft: [Z-002, SH-003, SH-002, SH-004, FA-004]
---

# F-010: Push-Benachrichtigungen

Geräte registrieren sich für Web Push (VAPID); Ereignisse wie Einladung, Verschiebung, Absage, Nachricht lösen Benachrichtigungen aus.

## Umsetzung (Ist)
`/api/v2/push` (public-key, subscribe, status, unsubscribe); Domain-Events im Backend.

## Ziele
- Z-002

## Stakeholder
- SH-003
- SH-002
- SH-004

## Anforderungen
- FA-004
