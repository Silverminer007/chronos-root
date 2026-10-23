# ADR-2026-10-22-016: Admin-Übersicht nutzt `/admin/stats` und `/admin/users` (Ticket #207)

Status: umgesetzt. Betrifft `frontend-angular/` und das Nuxt-Frontend `frontend/`.

**Ursache:** `AdminService` (Angular) und `pages/admin/index.vue` (Nuxt) riefen `GET /api/v2/admin/statistics` und `GET /api/v2/admin/user/?size=20` auf.
Beide Backends bieten nur `/api/v2/admin/stats` (`AdminStatisticsResource`, Rust `get_statistics`) und `/api/v2/admin/users` (`AdminUserResource`, Rust `list_users`);
die Seite zeigte „Fehler beim Laden – HTTP 404“. Der Fehler war wie bei #206 (ADR-015) von Nuxt nach Angular kopiert worden, und `admin.component.spec.ts`
bestätigte nur die falschen Pfade.

**Entscheidung:** Pfade in beiden Frontends korrigiert; `admin.service.spec.ts` (Reproduktion) und `admin.component.spec.ts` prüfen die Backend-Pfade.

**Offen:** `POST /admin/survey/{id}/notify` (Einladungs-Button) wurde in Java- und Rust-Backend nicht gefunden; separat prüfen.
