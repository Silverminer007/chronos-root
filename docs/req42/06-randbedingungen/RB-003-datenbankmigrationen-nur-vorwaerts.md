---
id: RB-003
title: Datenbankmigrationen nur vorwärts
status: entwurf
typ: randbedingung
quellen: [kb/constraints/C2025-09-28-004-forward-migrations.md]
verknuepft: []
---

# RB-003: Datenbankmigrationen nur vorwärts

Flyway-Migrationen `V<major>.<minor>.<patch>__beschreibung.sql` werden nach dem Deployment nie geändert; Fehler werden durch neue Migrationen behoben.

## Herkunft
Bisher: C2025-09-28-004.
