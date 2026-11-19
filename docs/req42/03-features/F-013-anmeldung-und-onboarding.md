---
id: F-013
title: Anmeldung und Onboarding
status: entwurf
typ: feature
quellen: [frontend/app/pages/onboarding.vue, kb/architecture/ARCH-2025-09-28-001-auth-flow.md]
verknuepft: [Z-004, SH-003, SH-002, FA-013]
---

# F-013: Anmeldung und Onboarding

Anmeldung per Keycloak (Authorization Code Flow); neue Benutzer mit unvollständigem Profil werden zu `/onboarding` geleitet.

## Umsetzung (Ist)
`/api/auth/*`-Server-Routen, `auth.global.ts`, Seite `onboarding.vue`.

## Ziele
- Z-004

## Stakeholder
- SH-003
- SH-002

## Anforderungen
- FA-013
