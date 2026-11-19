---
id: F-014
title: Profil und Konto
status: entwurf
typ: feature
quellen: [backend/.../presentation/UserResource.java, frontend/app/pages/profile.vue, kb/adr/ADR-2026-10-20-017-user-settings-keycloak-acl.md]
verknuepft: [Z-004, SH-003, FA-014]
---

# F-014: Profil und Konto

Profil bearbeiten und löschen, Konten verknüpfen und lösen, Passkeys verwalten, Passwort ändern.

## Umsetzung (Ist)
`/api/v2/user` mit `/linked`, `/link/{provider}`, `/passkeys`, `/change-password`; Seite `profile.vue`.

## Ziele
- Z-004

## Stakeholder
- SH-003

## Anforderungen
- FA-014
