---
id: BB-005
title: Java-Backend (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - backend/
  - CLAUDE.md
  - kb/architecture/ARCH-2025-09-28-003-layered-backend.md
verknuepft: [LS-005, BB-006, BB-008, BB-012, BB-007]
---

# Java-Backend (Ebene 1)

Quarkus 3, Java 21, Hibernate/Panache, Flyway, MapStruct, Lombok, Micrometer. Basis-Paket `de.chronos_live.chronos_date_api`.

| Paket | Baustein |
|---|---|
| `presentation/`, `application/`, `infrastructure/`, `domain/` | BB-006 |
| `application/events`, `application/ports` | BB-008 |
| `application/reminder`, `application/scheduler` | BB-012 |
| `security/` | BB-016 |
| `mapper/`, `dto/`, `exception/`, `config/` | Querschnitt (KZ-004, KZ-005) |

Ein separates Paket `de.chronos_live.admin` spiegelt die Struktur für Admin-Endpunkte (BB-015). Start: `./mvnw quarkus:dev`, Dev-UI unter `/q/dev/`.
