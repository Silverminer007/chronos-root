---
id: BB-016
title: Java-Backend: Security-Paket (Ebene 2)
status: entwurf
typ: baustein-ebene-2
quellen:
  - backend/.../security
  - kb/patterns/PATTERN-2025-09-28-001-principal-context.md
verknuepft: [KZ-001, KZ-002]
---

# Java-Backend: Security-Paket (Ebene 2)

`PrincipalContextFilter` liest das JWT einmal je Anfrage und schreibt die OIDC-Subject-ID in den request-scoped `PrincipalContext`; Services injizieren ihn. Markiert Anfragen unterhalb `/api/v2/admin/` als Admin-Anfragen. Rust: `security/{token,principal,middleware,admin,scopes}.rs`.
