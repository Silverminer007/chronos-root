---
title: Randbedingungen
status: entwurf
---

# Randbedingungen

Aus `kb/constraints/` übernommen. Nach der Freigabe sind **diese** Einträge maßgeblich, die alten Constraint-Dateien werden Verweise.

| ID | Randbedingung | Bisher |
|---|---|---|
| RB-001 | Alle UI-Texte sind deutsch. | C2025-09-28-001 |
| RB-002 | Anmeldung nur per OIDC (Keycloak). | C2025-09-28-002 |
| RB-003 | Datenbankmigrationen sind nur vorwärts gerichtet. | C2025-09-28-004 |
| RB-004 | Das Frontend ruft das Backend nie direkt auf, nur über Nuxt-Server-Routen. | C2025-09-28-005 |
| RB-005 | Änderungen an `main` nur per PR, nie direkt. | `CLAUDE.md` |
