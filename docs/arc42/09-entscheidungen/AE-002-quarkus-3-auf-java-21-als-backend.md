---
id: AE-002
title: "Quarkus 3 auf Java 21 als Backend"
status: entwurf
typ: architekturentscheidung
datum: 2025-09-28
quellen:
  - backend/pom.xml
  - backend/src/main/resources/application.properties
  - backend/src/main/docker/
  - .github/workflows/backend-ci.yml
  - deployment/values.yaml
  - kb/adr/ADR-2025-09-28-002-quarkus.md (Git-Historie, Begründung und Alternativen)
verknuepft: [AE-010, AE-011, AE-012]
---

# AE-002: Quarkus 3 auf Java 21 als Backend

**Entscheidung:** Das Backend ist eine Quarkus-Anwendung (`quarkus.platform.version` 3.37.x, Java 21) mit JAX-RS (`quarkus-rest-jackson`), CDI (Arc), Micrometer/Prometheus, SmallRye Health und OIDC. Zusätzlich liefert sie zeitgesteuerte Jobs (`quarkus-scheduler`, [AE-052](AE-052-zeitgesteuerte-jobs-mit-leader-election.md)) und Push ([AE-053](AE-053-web-push-direkt-per-vapid.md)). Persistenz: [AE-011](AE-011-postgresql-mit-flyway.md), [AE-012](AE-012-hibernate-panache-als-persistenz-api.md).

**Begründung (laut früherem ADR):** Geringer Ressourcenbedarf und schnelle Starts im Kubernetes-Betrieb, eingebaute Erweiterungen für Observability, Security und ORM, CDI-Events für entkoppelte Nebenwirkungen ([AE-048](AE-048-domaenenereignisse-ueber-cdi-events.md)), Live-Reload im Dev-Modus (`make backend`).

**Alternativen (laut ADR):** Spring Boot (größere Images), Micronaut, Vert.x, Helidon.

**Konsequenzen:**
- Die CI baut je Push auf `main`/`develop` ein JVM-Image (`Dockerfile.jvm`) und ein natives Image (`-Dnative`, `Dockerfile.native`, Tag `*-native`). Das Helm-Chart nutzt den Tag `latest`, also das **JVM-Image**; das native Image wird gebaut, aber nicht ausgerollt.
- Native Builds brauchen GraalVM (Profil `native` in `pom.xml`, `setup-graalvm` in der CI).
- Eine Plattformbindung besteht an Quarkus-Erweiterungen (Panache, Scheduler, OIDC).
