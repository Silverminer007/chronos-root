# ADR-2026-10-21-001: Angular statt Nuxt für das Frontend

Status: vorgeschlagen (Branch `angular-frontend`).

Kontext: Vorgabe des Nutzers, das Frontend auf Angular mit SCSS und Bootstrap zu migrieren.
Entscheidung: Neues Projekt `frontend-angular/` (Standalone, Signals), eigener Node-BFF für Auth-Cookies und Proxy, handgeschriebener Service Worker, kein NgRx.
Folgen: Zwei Frontends parallel bis zum Cutover; Helm-Chart unverändert, bis das Image gewechselt wird. Details: `kb/architecture/ARC42-angular-frontend.md`.
