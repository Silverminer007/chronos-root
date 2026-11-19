---
id: BB-007
title: Rust-Backend (Ebene 1)
status: entwurf
typ: baustein-ebene-1
quellen:
  - rust-backend/Cargo.toml
  - rust-backend/src
  - kb/adr
verknuepft: [LS-008, BB-017]
---

# Rust-Backend (Ebene 1)

axum 0.7, tokio, sqlx 0.8 (Postgres), jsonwebtoken, reqwest (rustls), chrono, tracing. Selbes Schema und dieselbe API wie Java (QA-001).

| Modul | Aufgabe |
|---|---|
| `appointments` | Termine, Teilnahme, Nachrichten, Events und Listener |
| `groups` | Gruppen, Freundschaften, Autorisierung |
| `users` | Benutzer, Einstellungen, Identität |
| `push_notifications` | Subscriptions, Versand, Log |
| `reminders` | Regeln, Scheduler, Leader-Election, Clock |
| `event_bus` | Postgres LISTEN/NOTIFY |
| `keycloak` | Anti-Corruption-Layer |
| `security` | Token, Principal, Admin-Extractor, Scopes |
| `admin` | `/api/v2/admin/*` |
| `database`, `error.rs`, `metrics.rs`, `app.rs` | Infrastruktur |
