---
id: KZ-002
title: Autorisierung
status: entwurf
typ: konzept
quellen:
  - kb/patterns/PATTERN-2025-09-28-005-authorization.md
  - backend/.../application/AuthorizationService.java
verknuepft: [BB-006, BB-015, LZ-003]
---

# Autorisierung

Berechtigungen werden im `AuthorizationService` geprüft, nicht in JAX-RS und nicht in Repositories. Maßstab ist die `UserRole` am Termin (geordnet): `NONE`, `GUEST`, `ATTENDANT`, `HELPER`, `RESPONSIBLE`.

| Aktion | Mindestrolle |
|---|---|
| Termin/Teilnehmer lesen, Nachrichten | jede Rolle außer `NONE` |
| Teilnehmer ändern, Gruppen hinzufügen, Rollen ändern, Termin ändern/absagen/löschen | `RESPONSIBLE` |

Admin-Anfragen (`/api/v2/admin/*`, Rolle `ADMIN_API`) überspringen die Teilnehmerprüfung. Die genaue Matrix je Methode steht im Code **(?)**. Rust: `groups/authorization.rs`, Admin-Extractor.
