# ADR-2026-10-23-032: Abgesagte Termine bleiben im Rust-Backend abrufbar

Status: akzeptiert (Ticket #245)

## Kontext
Im Rust-Backend schloss die Konstante `ACTIVE` (`status NOT IN (1, 2)`) CANCELLED und DELETED aus und wurde in
`find_by_id`, `find_by_creator` und `find_by_date_range` benutzt. GET/PATCH auf einen abgesagten Termin lieferte
dadurch 404, obwohl er in der Agenda-Liste erschien. Java blendet nur DELETED aus (`getAppointment`, `search`);
nur der Erinnerungs-Scheduler (`findNonCancelledBetween`) schließt CANCELLED aus.

## Entscheidung
- Neue Konstanten `NOT_DELETED`/`NOT_DELETED_ALIASED` (`status <> 2`) für `find_by_id`, `find_by_creator`, `find_by_date_range`.
- `ACTIVE` bleibt ausschließlich für `find_non_cancelled_between`.
- DELETED liefert weiterhin 404.

## Test
Integrationstest `rust-backend/tests/cancelled_appointment_access_test.rs`.
