---
id: KZ-005
title: Fehlerbehandlung
status: entwurf
typ: konzept
quellen:
  - kb/patterns/003-error-handling.md
  - backend/.../exception
  - kb/adr/ADR-2026-10-20-025-central-error-mapping.md
verknuepft: [KZ-011, BB-006]
---

# Fehlerbehandlung

Java: Exception-Hierarchie (`BadRequest`, `Forbidden`, `ResourceNotFound`, `Validation`) mit JAX-RS-`ExceptionMapper`, Antwort als `ErrorResponse`. Das Backend sendet technische Fehler/Codes, nie nutzerseitigen Text; das Frontend übersetzt ins Deutsche (RB-001). Rust: zentrales Mapping in `error.rs` (ADR-025). Ausnahme: `@RolesAllowed`-Verweigerung bei Admin liefert leere 403.
