---
id: QS-007
title: "Zugriffsschutz der Admin-API"
status: entwurf
typ: qualitaetsszenario
quellen:
  - kb/adr/ADR-2026-10-20-023-admin-endpoints.md
verknuepft: [FA-007]
---

# QS-007: Zugriffsschutz der Admin-API

**Szenario:** Anonym, normaler Benutzer und Admin rufen alle 14 Admin-Routen des Rust-Backends auf. **Erwartung:** 401 ohne Token, leeres 403 ohne Rolle `ADMIN_API`, Zugriff mit Rolle. **Nachweis:** `admin_endpoints_test` im Rust-Backend.
