---
id: BB-015
title: Admin-Bereich (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - backend/.../admin
  - rust-backend/src/admin
  - kb/adr/ADR-2026-10-20-023-admin-endpoints.md
  - frontend/app/pages/admin
verknuepft: [FA-007, KZ-002]
---

# Admin-Bereich (Ebene 1)

Endpunkte `/api/v2/admin/*` (Java: sechs `Admin*Resource`, Rolle `ADMIN_API`): Benutzer, Termine, Gruppen, Freundschaften, Statistik, Push-Log. Admin-Anfragen umgehen die Teilnehmerprüfungen (`isAdminRequest`); im Rust-Backend erzwingt der Extractor `AdminPrincipal` die Rolle (ohne Rolle leere 403). Frontend: `admin/index.vue`, `admin/push.vue`.
