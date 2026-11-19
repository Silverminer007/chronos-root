---
id: AE-045
title: "Small parity deviations (health data, trailing slash, settings 400)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-20
quellen:
  - kb/adr/ADR-2026-10-20-028-small-parity-deviations.md
verknuepft: []
---

# AE-045: Small parity deviations (health data, trailing slash, settings 400)

Quelle: `kb/adr/ADR-2026-10-20-028-small-parity-deviations.md` (Status im ADR: accepted, Datum: 2026-10-20). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

(?) im ADR nicht ausgewiesen.

## Entscheidung

1. **`/q/health/ready`** carries `checks[].data` like SmallRye/Agroal: `{"<default>": "UP|DOWN"}` (`<default>` is Quarkus' name of the default datasource, `DEFAULT_DATASOURCE` in `app.rs`). The allowlist entry for it is removed.
2. **Trailing slash:** `build_router` wraps the whole router in tower-http `NormalizePath::trim_trailing_slash`, so `/api/v2/groups/` is routed like `/api/v2/groups` (Java maps `@Path("/")`). One wrapper instead of a second route per collection; it also covers future routes. It replaces the per-route trailing-slash mechanism of [ADR-2026-10-20-023](../../../kb/adr/ADR-2026-10-20-023-admin-endpoints.md) (point 4): the duplicate routes (`with_trailing_slash`) in `admin/mod.rs` were redundant and are removed, so there is a single mechanism. The router is still returned as `Router` (via `fallback_service`), so `main.rs` and tests are unchanged …

## Konsequenzen

(?) im ADR nicht ausgewiesen.
