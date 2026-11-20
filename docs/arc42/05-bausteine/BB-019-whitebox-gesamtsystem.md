---
id: BB-019
title: Whitebox Gesamtsystem (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - frontend/
  - backend/
  - deployment/
verknuepft: [BB-001, BB-005, BB-015, BB-018, KT-002]
---

# Whitebox Gesamtsystem (Ebene 1)

```mermaid
flowchart LR
  subgraph Browser
    SPA[SPA: Seiten, Stores] --> SW[Service Worker]
  end
  SW --> NS
  subgraph Frontend-Container
    NS[Nuxt-Server-Routen]
  end
  NS -->|Bearer| RES
  subgraph Backend-Container
    RES[Ressourcen] --> SRV[Services] --> REPO[Repositories]
    SRV --> PORT[Ports und Adapter]
    SRV -. Events .-> OBS[Beobachter]
  end
  REPO --> DB[(PostgreSQL)]
  PORT --> KC[Keycloak]
  PORT --> PUSH[Push-Dienste]
```

| Baustein | Verantwortung | Schnittstelle nach außen |
|---|---|---|
| [Frontend](BB-001-frontend.md) | Oberfläche, Anmeldestatus, Zustand, Push-Anzeige | `/api/*` am Nuxt-Server |
| [Java-Backend](BB-005-java-backend.md) | Fachlogik, Autorisierung, Persistenz, Benachrichtigung, Zeitsteuerung | REST `/api/v2/**` (JSON), Management-Port 9000 |
| [Admin-Bereich](BB-015-admin-bereich.md) | Betreuung durch Administratoren (Teil von Frontend und Backend) | `/api/v2/admin/**` |
| [Datenbank](BB-018-datenbank.md) | Schema und Migrationen | JDBC |

**Begründung der Zerlegung:** Der Browser kennt nur den Nuxt-Server, der als Proxy die Tokens führt ([LS-002](../04-loesungsstrategie/LS-002-server-routen-als-proxy.md)); das Backend bündelt Fachlogik und Zeitsteuerung, ist zustandslos bis auf die Datenbank und deshalb mehrfach instanziierbar ([LS-012](../04-loesungsstrategie/LS-012-leader-election-fuer-zeitgesteuerte-aufgaben.md)); Identitätsdaten bleiben in Keycloak ([LS-003](../04-loesungsstrategie/LS-003-oidc-mit-keycloak.md)).

Die Auslieferung als Kubernetes-Ressourcen beschreibt [Kapitel 7](../07-verteilung/README.md), nicht die Bausteinsicht.
