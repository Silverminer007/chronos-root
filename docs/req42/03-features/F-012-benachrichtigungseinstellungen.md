---
id: F-012
title: Benachrichtigungseinstellungen
status: entwurf
typ: feature
quellen: [backend/.../presentation/SettingsResource.java, frontend/app/types/index.ts, frontend/app/pages/settings.vue]
verknuepft: [Z-002, SH-003, SH-002, SH-004, FA-012]
---

# F-012: Benachrichtigungseinstellungen

Benutzer stellen je Ereignisart ein, ob und für welche Rollen sie benachrichtigt werden (DISABLED, ALL, ATTENDANT, HELPER, RESPONSIBLE); Gruppen-Einstellung ENABLED/DISABLED.

## Umsetzung (Ist)
`GET/PUT /api/v2/settings`; Seite `settings.vue`.

## Ziele
- Z-002

## Stakeholder
- SH-003
- SH-002
- SH-004

## Anforderungen
- FA-012
