# ADR-2026-10-22-030: Access token audience is not validated by default

- Status: accepted
- Ticket: #174 (Rust parity follow-up to #169)

## Context

`TokenValidator` used `Validation::new(RS256)` without an audience. `jsonwebtoken` rejects every token that contains `aud` with `InvalidAudience` when no expected audience is set. Keycloak adds `"aud": "account"` to the token of every user with the default roles (`default-roles-<realm>`), so the Rust backend answered 401 for all normally created users. Java (Quarkus OIDC) accepts them because it does not check `aud` unless `quarkus.oidc.token.audience` is configured. The parity realm users only had the role `user` (no `aud`), so #169 did not see it.

## Decisions

1. **Default: `validate_aud = false`**, the Quarkus default. Signature, expiry and key id stay validated; the issuer is not checked in either backend.
2. **Optional `AUTH_SERVER_AUDIENCE`.** If set (non-empty), `TokenValidator::with_audience` requires it in `aud` (string or array). A mismatch is `TokenError::InvalidToken` (401). Unset is the default, so existing deployments need no change. Chosen over a hard-coded audience because the `aud` of a realm depends on its client scope mappers.
3. **Harness:** `parity-user-2` gets `default-roles-chronos` (token with `aud: account`), so the whole contract scenario runs with such a user (it takes part in every multi-user step). `run-contract.sh` fails if that token has no `aud`, so the case cannot silently disappear. `parity-user-1` and `parity-admin` stay without `aud`; both token shapes are covered.

## Consequences

- Tests: `rust-backend/tests/token_audience_test.rs` (no `aud`, `aud: account`, `aud` array, configured audience hit/miss).
- Java is unchanged.
