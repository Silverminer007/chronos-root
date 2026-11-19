---
id: FA-013
title: Anmeldung über Keycloak und Onboarding
status: entwurf
typ: anforderung
quellen: [CLAUDE.md]
verknuepft: [F-013]
---

# FA-013: Anmeldung über Keycloak und Onboarding

Benutzer melden sich über Keycloak an; fehlt ein vollständiges Profil, werden sie zum Onboarding geleitet.

## Akzeptanzkriterien
- Ohne gültige Sitzung erfolgt Weiterleitung auf `/`.
- Mit unvollständigem Profil erfolgt Weiterleitung auf `/onboarding`.

## Features
- F-013

## Priorität
**(?)** vom Product Owner festzulegen.
