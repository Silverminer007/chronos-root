Projektregeln chronos-root (Rust-Backend `rust-backend/`, Quarkus-Backend `backend/`, Nuxt-Frontend `frontend/`, Helm `deployment/`, Wissensbasis `kb/`):
- `CLAUDE.md` des Repos gilt (u. a.: nie direkt auf main, Änderungen per PR; die Oberfläche ist deutsch).
- Testumgebung im Pod (kein Docker, kein Testcontainers): Ist `$TEST_DATABASE_URL` gesetzt (Postgres-Sidecar, Label `area:rust`), setze zusätzlich
  `export DATABASE_URL=$TEST_DATABASE_URL` und führe `cd rust-backend && cargo fmt --check && cargo clippy --all-targets -- -D warnings && timeout 2400 cargo test`
  aus (Ausgabe in eine Datei, nur das Ende zeigen). Frontend: `cd frontend && npm ci && npm run lint && npm run typecheck && npm run build`.
  Java: `cd backend && ./mvnw -B -q -DskipTests verify`; Quarkus-Tests brauchen Docker und laufen im Pod nicht: nenne das im PR, die GitHub-Actions prüfen sie.
- PR-Titel `Resolve #<Ticket>: <Titel>`, Body beginnt mit `Closes #<Ticket>`.
