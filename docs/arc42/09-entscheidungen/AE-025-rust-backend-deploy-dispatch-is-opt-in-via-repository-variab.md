---
id: AE-025
title: "Rust backend deploy dispatch is opt-in via repository variable"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-018-rust-deploy-opt-in.md
verknuepft: []
---

# AE-025: Rust backend deploy dispatch is opt-in via repository variable

Quelle: `kb/adr/ADR-2026-10-20-018-rust-deploy-opt-in.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

The `deploy` job in `.github/workflows/backend-rust-test.yml` failed on every push to `main` (ticket #140): it has no checkout step, but the workflow-level `defaults.run.working-directory` is `rust-backend`, so the shell could not start. Had it worked, it would have sent `repository_dispatch` `deploy-production` (main) or `deploy-staging` (develop) with `service: rust-backend`. The Rust backend must not be deployed before API parity with the Java backend is proven (#137).

## Entscheidung

- The `deploy` job runs only if the repository variable `RUST_BACKEND_DEPLOY` equals `'true'`. Unset means off. A skipped job does not fail the workflow.
- The job overrides `defaults.run.working-directory` to `.`, so it does not depend on the `rust-backend` directory (no checkout is needed for `curl`).
- Enable via Settings > Secrets and variables > Actions > Variables, only after #137 is done and `deployment-ci.yml` can deploy the Rust image.

## Konsequenzen

(?) im ADR nicht ausgewiesen.
