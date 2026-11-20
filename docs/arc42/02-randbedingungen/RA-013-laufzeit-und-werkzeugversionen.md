---
id: RA-013
title: "Laufzeit- und Werkzeugversionen"
status: entwurf
typ: randbedingung
quellen:
  - backend/pom.xml
  - backend/src/main/docker/Dockerfile.jvm
  - frontend/package.json
  - frontend/Dockerfile
  - deployment/values.yaml
  - backend/docker-compose.yaml
  - .github/workflows
verknuepft: [KT-005, KZ-010, VT-007]
---

# RA-013: Laufzeit- und Werkzeugversionen

Die Technologiebasis ist durch folgende, im Code festgelegte Versionen vorgegeben:

| Bestandteil | Version | Beleg |
|---|---|---|
| Java (Compiler-Release, CI) | 21 | `maven.compiler.release`, `setup-java` |
| Quarkus | 3.37.3 | `quarkus.platform.version` |
| Laufzeit-Image Backend | `ubi9/openjdk-21` | `Dockerfile.jvm` |
| Nuxt / Vue / Pinia / PrimeVue / Tailwind | 4.4 / 3.5 / 3.0 / 4.5 / 4.x | `package.json` |
| Node.js (CI und Image) | 26 | `frontend-ci.yml`, `frontend/Dockerfile` |
| PostgreSQL im Cluster | 16 | `postgresql.image.tag` |
| PostgreSQL lokal | 18 | `backend/docker-compose.yaml` |

Der Unterschied zwischen Cluster (16) und lokaler Entwicklung (18) ist im Code nicht begründet **(?)**; Tests laufen mit Testcontainers-Standardimage von Quarkus ([KZ-010](../08-konzepte/KZ-010-teststrategie.md)). Der Native-Build des Backends (`Dockerfile.native`, Job `build-native`) ist in `backend-ci.yml` mit `if: false` abgeschaltet; ausgeliefert wird das JVM-Image.
