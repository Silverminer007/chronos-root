---
id: F-015
title: Administration
status: entwurf
typ: feature
quellen: [backend/.../admin/presentation/, frontend/app/pages/admin/, kb/adr/ADR-2026-10-20-023-admin-endpoints.md]
verknuepft: [Z-004, SH-005, FA-007]
---

# F-015: Administration

Admin-Oberfläche und `/api/v2/admin/*` für Benutzer, Termine, Gruppen, Freundschaften, Statistiken und Push-Versand; nur mit Rolle `ADMIN_API`.

## Umsetzung (Ist)
Seiten `admin/index.vue`, `admin/push.vue`.

## Ziele
- Z-004

## Stakeholder
- SH-005

## Anforderungen
- FA-007
