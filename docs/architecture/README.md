# Chronos Architektur-Diagramme (Excalidraw)

Gegliedert nach arc42 in vier Detailebenen. Dateien mit [excalidraw.com](https://excalidraw.com) oder dem VS-Code-Excalidraw-Plugin öffnen.

| Ebene | Datei | arc42 | Inhalt |
|---|---|---|---|
| 1 | `01-system-context.excalidraw` | §3 / §5.1 | Nutzer, Chronos, Keycloak, Web-Push, Monitoring |
| 2 | `02-containers.excalidraw` | §5.2 | Browser/PWA, Nitro, Quarkus, Rust-Port, PostgreSQL |
| 3a | `03a-frontend-components.excalidraw` | §5.3 | Pages, Stores, Middleware, Service Worker, Server-Routen |
| 3b | `03b-backend-components.excalidraw` | §5.3 | Presentation, Application, Events, Reminder, Infrastructure |
| 4a | `04a-flow-push-events.excalidraw` | §6 | Termin anlegen → CDI-Event → Web-Push |
| 4b | `04b-flow-auth-refresh.excalidraw` | §6 | OIDC-Login, Cookies, Service-Worker-Token-Refresh |
| – | `05-deployment.excalidraw` | §7 | Kubernetes/Helm-Verteilung |
