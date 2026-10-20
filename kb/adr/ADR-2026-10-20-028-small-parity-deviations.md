# ADR-2026-10-20-028: Small parity deviations (health data, trailing slash, settings 400)

- Status: accepted
- Ticket: #161 (found by the parity run of #137)

## Decision

1. **`/q/health/ready`** carries `checks[].data` like SmallRye/Agroal: `{"<default>": "UP|DOWN"}` (`<default>` is Quarkus' name of the default datasource, `DEFAULT_DATASOURCE` in `app.rs`). The allowlist entry for it is removed.
2. **Trailing slash:** `build_router` wraps the whole router in tower-http `NormalizePath::trim_trailing_slash`, so `/api/v2/groups/` is routed like `/api/v2/groups` (Java maps `@Path("/")`). One wrapper instead of a second route per collection; it also covers future routes. The router is still returned as `Router` (via `fallback_service`), so `main.rs` and tests are unchanged. `tower-http` moved from dev- to normal dependency (feature `normalize-path`).
3. **Unknown route:** already answered with the Java JSON 404 since #157 ([ADR-2026-10-20-025](ADR-2026-10-20-025-central-error-mapping.md)); nothing to change. A wrong method stays `405` (Java answers 500, a Java defect, see #162; not copied).
4. **`PUT /api/v2/settings` with syntactically broken JSON** (`[1,`) is `400 BAD_REQUEST` (`parse_body_or_bad_request` in `users/handlers`). The JSON syntax is checked on its own, because serde reports a type mismatch (`[` is not a settings object) before it sees the later syntax error. Empty body, `null` and wrong field values stay `500` as pinned in ADR-2026-10-20-017. `PATCH /api/v2/user` keeps its 500 for unreadable bodies: the ticket only names settings and an existing test pins that behaviour.
5. **`POST /api/v2/push/subscribe` without keys:** Rust already answers 500 with the JSON error body since #157. The status (Java NPE defect) follows the decision in the Java ticket (#162); no change here.
6. **`GET /admin/users/{unknown}`:** Rust keeps `404`; Java's 500 is a defect, to be decided in the Java ticket (#162).

## Tests

`readiness_probe_integration_test.rs` (data UP/DOWN, trailing slash), `user_endpoints_test.rs::put_settings_with_malformed_json_is_400`.
