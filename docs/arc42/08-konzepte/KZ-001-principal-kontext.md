---
id: KZ-001
title: Principal-Kontext
status: entwurf
typ: konzept
quellen:
  - kb/patterns/PATTERN-2025-09-28-001-principal-context.md
  - backend/.../security
verknuepft: [BB-016, LS-003]
---

# Principal-Kontext

Die OIDC-Subject-ID wird nicht durch alle Methodensignaturen gereicht, sondern einmal je Anfrage im `PrincipalContextFilter` gelesen und im request-scoped Bean `PrincipalContext` abgelegt. Services injizieren das Bean. Folgen: kurze Signaturen, ein Ort für die Identität; Hintergrundjobs (Scheduler, Event-Listener) haben keinen Principal und brauchen die ID explizit. Rust: `security/principal.rs` (UUID, ADR-011).
