---
id: AE-index
title: "9 Architekturentscheidungen"
status: entwurf
typ: index
quellen:
  - backend/
  - frontend/
  - deployment/
  - .github/workflows/
verknuepft: []
---

# 9 Architekturentscheidungen

Eine Datei pro Entscheidung (`AE-nnn`) mit Entscheidung, Begründung, Alternativen und Konsequenzen. Aufgenommen sind nur Entscheidungen, die im Code erkennbar sind. AE-001 bis AE-014 sind bereits früh dokumentiert; Begründung und Alternativen stammen dort aus den früheren ADRs (in der Git-Historie unter `kb/adr/`, im Code nicht mehr belegt). Wo eine Begründung nirgends steht, ist sie mit **(?)** markiert. Das `datum` der Einträge ab AE-048 ist der früheste Git-Commit des Kernbausteins, nicht der Beschluss.

IDs sind stabil und werden nicht wiederverwendet; Lücken (AE-004 bis AE-007, AE-015 bis AE-047) sind entfallene Einträge. Ihre noch gültigen Aussagen über das Rechte-, Lösch- und Teilnahmeverhalten stehen in [AE-050](AE-050-termine-per-statuswechsel-loeschen-teilnahmen-hart.md) und [AE-051](AE-051-berechtigung-ueber-teilnehmerrolle-am-termin.md).

| ID | Entscheidung | Bereich |
|---|---|---|
| [AE-001](AE-001-nuxt-und-vue-als-spa-ohne-ssr.md) | Nuxt und Vue als SPA ohne SSR | Frontend |
| [AE-002](AE-002-quarkus-3-auf-java-21-als-backend.md) | Quarkus 3 auf Java 21 als Backend | Backend |
| [AE-003](AE-003-keycloak-oidc-fuer-die-authentifizierung.md) | Keycloak OIDC für die Authentifizierung | Sicherheit |
| [AE-008](AE-008-token-refresh-im-service-worker.md) | Token-Refresh im Service Worker | Frontend, Sicherheit |
| [AE-009](AE-009-pinia-fuer-den-frontend-zustand.md) | Pinia für den Frontend-Zustand | Frontend |
| [AE-010](AE-010-geschichtetes-backend-mit-ports.md) | Geschichtetes Backend mit Ports | Backend |
| [AE-011](AE-011-postgresql-mit-flyway.md) | PostgreSQL mit Flyway | Daten |
| [AE-012](AE-012-hibernate-panache-als-persistenz-api.md) | Hibernate Panache als Persistenz-API | Backend, Daten |
| [AE-013](AE-013-nuxt-serverrouten-als-auth-proxy.md) | Nuxt-Serverrouten als Auth-Proxy | Frontend, Sicherheit |
| [AE-014](AE-014-austauschbarer-secret-provider-im-helm-chart.md) | Austauschbarer Secret-Provider im Helm-Chart | Betrieb |
| [AE-048](AE-048-domaenenereignisse-ueber-cdi-events.md) | Domänenereignisse über CDI-Events | Backend |
| [AE-049](AE-049-lokaler-profil-cache-hinter-identityport.md) | Lokaler Profil-Cache hinter IdentityPort | Backend, Daten |
| [AE-050](AE-050-termine-per-statuswechsel-loeschen-teilnahmen-hart.md) | Termine per Statuswechsel löschen, Teilnahmen hart | Daten |
| [AE-051](AE-051-berechtigung-ueber-teilnehmerrolle-am-termin.md) | Berechtigung über Teilnehmerrolle am Termin | Sicherheit |
| [AE-052](AE-052-zeitgesteuerte-jobs-mit-leader-election.md) | Zeitgesteuerte Jobs mit Leader-Election | Backend, Betrieb |
| [AE-053](AE-053-web-push-direkt-per-vapid.md) | Web-Push direkt per VAPID | Backend, Frontend |
| [AE-054](AE-054-kubernetes-mit-helm-und-github-actions.md) | Kubernetes mit Helm und GitHub Actions | Betrieb |
| [AE-055](AE-055-gruppeneinladung-als-abgeleitete-einzelteilnahmen.md) | Gruppeneinladung als abgeleitete Einzelteilnahmen | Daten, Backend |
| [AE-056](AE-056-zeitpunkte-als-utc-instant.md) | Zeitpunkte als UTC-Instant, Fristregeln in UTC | Backend |
