---
id: AE-012
title: "Hibernate Panache als Persistenz-API"
status: entwurf
typ: architekturentscheidung
datum: 2025-09-28
quellen:
  - backend/pom.xml
  - backend/src/main/java/de/chronos_live/chronos_date_api/domain/
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/
  - kb/adr/ADR-2025-09-28-009-panache.md (Git-Historie, Begründung und Alternativen)
verknuepft: [AE-002, AE-011]
---

# AE-012: Hibernate Panache als Persistenz-API

**Entscheidung:** Entities erweitern `PanacheEntity` (ID, Active-Record-Methoden); Abfragen stehen in Repository-Klassen, die `PanacheRepository<T>` implementieren (`infrastructure`). Hibernate ORM bleibt darunter; das Schema verwaltet Flyway, nicht Hibernate ([AE-011](AE-011-postgresql-mit-flyway.md)).

**Begründung (laut früherem ADR):** Weniger Boilerplate als reines JPA (kein `EntityManager`, eingebaute Paginierung), vertraute Muster.

**Alternative:** Reines Hibernate/JPA mit `EntityManager`.

**Konsequenzen:** Entities sind an Panache und damit an Quarkus gebunden; Abfragen sind überwiegend JPQL-/HQL-Strings ohne Compile-Prüfung. Unit-Tests mocken Repositories (`quarkus-panache-mock`).
