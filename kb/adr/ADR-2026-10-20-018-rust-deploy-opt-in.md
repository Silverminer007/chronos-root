---
title: ADR-2026-10-20-018: Rust backend deploy dispatch is opt-in via repository variable
date: 2026-10-20
status: Accepted
supercedes: (none)
superceded_by: (none)
---

## Context

The `deploy` job in `.github/workflows/backend-rust-test.yml` failed on every push to `main` (ticket #140): it has no checkout step, but the workflow-level `defaults.run.working-directory` is `rust-backend`, so the shell could not start. Had it worked, it would have sent `repository_dispatch` `deploy-production` (main) or `deploy-staging` (develop) with `service: rust-backend`. The Rust backend must not be deployed before API parity with the Java backend is proven (#137).

## What `deployment-ci.yml` does with `service: rust-backend`

The "Override version" step only handles `service` = `backend` or `frontend`. For `rust-backend` no override is written, so `overrides.yaml` stays empty and `helm upgrade chronos . -f values-<env>.yaml` runs with the unchanged values. The Rust image is therefore **not** rolled out today; a dispatch would only trigger a redundant redeploy of the current chart. Rolling out the Rust image requires a chart change plus a new `rust-backend` branch in that step, which is out of scope here.

## Decision

- The `deploy` job runs only if the repository variable `RUST_BACKEND_DEPLOY` equals `'true'`. Unset means off. A skipped job does not fail the workflow.
- The job overrides `defaults.run.working-directory` to `.`, so it does not depend on the `rust-backend` directory (no checkout is needed for `curl`).
- Enable via Settings > Secrets and variables > Actions > Variables, only after #137 is done and `deployment-ci.yml` can deploy the Rust image.

## Alternatives considered

| Option | Result |
|---|---|
| Remove the dispatch steps | Loses the wiring that is needed later; re-adding is more work than flipping a variable. |
| Add a checkout step | Fixes the failure but needs a clone for a single `curl`. |
