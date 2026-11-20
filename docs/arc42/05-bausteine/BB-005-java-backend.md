---
id: BB-005
title: Java-Backend (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - backend/pom.xml
  - backend/src/main/resources/application.properties
  - backend/src/main/java/de/chronos_live
  - backend/src/main/docker/Dockerfile.jvm
verknuepft: [LS-005, BB-006, BB-008, BB-012, BB-013, BB-015, BB-016]
---

# Java-Backend (Ebene 1)

Quarkus 3.37 auf Java 21. Wichtige Erweiterungen: REST mit Jackson, OIDC, Hibernate ORM mit Panache, Flyway, Scheduler, Kubernetes-Client (Leader-Election), Micrometer mit Prometheus, SmallRye Health, OpenAPI, Hibernate Validator, Keycloak-Admin-REST-Client; dazu MapStruct, Lombok, `web-push` mit BouncyCastle und JaCoCo. Basis-Paket `de.chronos_live.chronos_date_api`, Admin-Paket `de.chronos_live.admin`.

| Paket | Baustein |
|---|---|
| `presentation/`, `application/`, `infrastructure/`, `domain/` | [BB-006](BB-006-java-schichten.md) |
| `application/events`, `application/ports` | [BB-008](BB-008-events-und-ports.md) |
| `application/reminder`, `application/scheduler`, Zeitsteuerung in `application` | [BB-012](BB-012-java-reminder.md) |
| `WebPushService`, `WebPushAdapter`, `PushSubscriptionService` | [BB-013](BB-013-push-benachrichtigungen.md) |
| `security/` | [BB-016](BB-016-java-security.md) |
| `de.chronos_live.admin` | [BB-015](BB-015-admin-bereich.md) |
| `dto/`, `mapper/` | [KZ-004](../08-konzepte/KZ-004-dto-und-mapping.md) |
| `exception/` | [KZ-005](../08-konzepte/KZ-005-fehlerbehandlung.md) |
| `config/` | `PushConfig` (VAPID-Werte), `ClockProducer` (liefert `Clock.systemUTC()`) |
| `src/main/resources` | `application.properties`, Migrationen ([BB-018](BB-018-datenbank.md)) |

**Schnittstellen:** REST `/api/v2/**` auf Port 8080; Management-Schnittstelle (Health, Metriken) auf Port 9000. **Konfiguration** nur über Umgebungsvariablen ([KZ-013](../08-konzepte/KZ-013-konfiguration.md)). **Start:** `./mvnw quarkus:dev` (Dev-UI unter `/q/dev/`), Auslieferung als JVM-Image (`Dockerfile.jvm`, `ubi9/openjdk-21`).
