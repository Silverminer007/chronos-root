---
title: ARCH-2026-10-20-001: Parity run of the Rust backend against the Java reference in the local k3s cluster
date: 2026-10-20
component: Rust backend, Deployment, Contract tests
relates_to:
  - ADR-2026-10-20-024: Java and Rust backend side by side in the local k3s cluster
  - ADR-2026-10-20-021: Contract parity harness for the Rust port
  - ADR-2026-10-20-018: Rust backend deploy dispatch is opt-in
---

## Overview

Ticket #137. The Rust backend (`rust-backend/`) was deployed next to the Java backend (`backend/`, reference) in the single-node k3s cluster of the workstation, namespace `chronos-parity`, and compared with the contract harness (`contract-tests/`, #136) and a hand-written explorative pass (`deployment/parity/explore.py`). **Result: not at parity yet.** The run found 7 groups of Rust deviations and 2 groups of Java defects; each has a ticket (table below). Nothing was deployed outside the namespace; the Rust backend is not in staging or production (#140).

## Environment of the run

| Item | Value |
|---|---|
| Date | 2026-10-20, 11:30-15:45 UTC |
| Commit under test | `main` at `53b4016` (all endpoint tickets #96-#102 and #116-#125 merged); #153 was fixed on `main` (`5b6dd8f`) during the run |
| Cluster | k3s v1.36.5+k3s1, single node, containerd 2.3.4 |
| Helm | 3.16.4 (installed to `~/.local/bin`, was not present) |
| Keycloak | 26.0.7, dev mode, realm `chronos` imported from `deployment/parity/chronos-realm.json` |
| PostgreSQL | 16, one instance, databases `chronos` (Java, Flyway) and `chronos_rust` (Rust, sqlx migrations) |
| Java backend | built from the checkout (`chronos-backend` 1.2.0, Quarkus 3.37.3, JDK 21 in a Maven container), see "The Java participation routes" |
| Rust backend | built from the checkout with `rust-backend/Dockerfile` (Rust 1.98.1, musl) |
| Harness | `contract-tests` of this change (second user, management URL, path normalisation added, see below) |

Deployment steps and every command are in [`deployment/parity/README.md`](../../deployment/parity/README.md); the decisions are in ADR-2026-10-20-024.

### Commands (short form)

```bash
# images (details in the README)
docker build -t chronos-rust-backend:parity rust-backend
docker save chronos-rust-backend:parity | sudo k3s ctr -n k8s.io images import -
sudo k3s ctr -n k8s.io images label docker.io/library/chronos-rust-backend:parity io.cri-containerd.pinned=pinned
# cluster
kubectl create namespace chronos-parity
kubectl -n chronos-parity create configmap keycloak-realm --from-file=deployment/parity/chronos-realm.json
kubectl apply -f deployment/parity/keycloak.yaml
helm upgrade --install chronos deployment -n chronos-parity -f deployment/values-local.yaml -f deployment/values-parity.yaml \
  --set-file secrets.values.vapid_public_key=... --set-file secrets.values.vapid_private_key=...
# readiness (acceptance criterion): Rust port 8080, Java management port 9000
kubectl -n chronos-parity port-forward svc/chronos-rust-backend 18081:80 &
curl -s -o /dev/null -w '%{http_code}\n' localhost:18081/q/health/ready          # 200
# fresh databases + fresh Keycloak, then the harness and the explorative pass
deployment/parity/reset-databases.sh
deployment/parity/run-contract.sh                  # python3 -m parity
deployment/parity/reset-databases.sh
deployment/parity/run-contract.sh --explore        # deployment/parity/explore.py
```

## Findings about the environment (not about parity)

1. **The CI image `ghcr.io/silverminer007/chronos-backend:latest` is not a valid reference.** It was built 2026-07-18 and lacks the admin API of #55 (every `/api/v2/admin/*` route except push answered 404). Both images are built from the checkout (ADR-2026-10-20-024).
2. **Quarkus serves health and metrics on the management port** (`quarkus.management.enabled=true`, 9000). The harness sent `/q/health/*` and `/metrics` to the API port of Java and got 404. Fixed in the harness: `--java-management-url` / `CONTRACT_JAVA_MANAGEMENT_URL`.
3. **kubelet image garbage collection** removes locally imported images that no pod uses once the disk is above ~85 % (it did, while a backend was scaled to 0): the pod fails with `ErrImageNeverPull`. Pin them: `ctr images label ... io.cri-containerd.pinned=pinned`.
4. **Java sends a verification mail when the e-mail of a user changes**; Keycloak has no SMTP here, so `PATCH /api/v2/user` answered 500 on Java and 200 on Rust. The realm user `parity-user-1` already has the e-mail the scenario patches to (not a parity finding).
5. **Host load** reached 95 during the run (other agents); the reset steps ran into timeouts, `reset-databases.sh` now waits up to 300 s.

## Harness gaps found and fixed in this change

| Gap | Fix |
|---|---|
| One user only; Java lets only friends be added to groups and appointments (404 "Ihr seid nicht befreundet"), so every participant step cascaded into 404 | Role `user2` (`CONTRACT_USER2_TOKEN`, defaults to the first user) and a "befriend" prelude in the scenario; the friendship section ends the friendship before it re-creates requests, and `list friends` is no longer empty |
| Health and metrics on a different port | `HttpTarget(management_url=...)` routes `/q/health*` and `/metrics` there |
| `path` in error bodies contains generated ids (`/admin/appointments/2/...` vs `/51/...`) | `path` values are compared with digits replaced |
| `admin create group` returns a bare number, the capture used `id` (all later group steps ran with id `0`) | capture `$` |
| `admin send message` needs `timestamp` (Java NPE otherwise) | field added |

Unit tests for all of them are in `contract-tests/tests`.

## Results

### Rust pod not Ready (#153, fixed on main)

At `53b4016` `/q/health/ready` answered 503 `Database connections health check: DOWN` forever: `PostgresEventBus::subscribe` opened one `PgListener::connect_with(&pool)` per event type (16) and each held a pool connection, the default pool is 16. `DATABASE_MAX_CONNECTIONS=40` showed the cause (17 connections). #153 was fixed on `main` (`5b6dd8f`) during the run; the Rust image must be rebuilt from a commit at or after it and `DATABASE_MAX_CONNECTIONS` removed from `values-parity.yaml` (done, see "Final harness run").

### Contract harness

The first full run on `53b4016` (Java: intended routing, see below) had 81 steps, 16 mismatches; the final run on `5b6dd8f` ("Final harness run" below) has 2. Every mismatch is either in `contract-tests/allowlist.json` with its ticket in the reason, or is of kind `missing_route`, which the allowlist refuses by design (ADR-2026-10-20-021) and which therefore keeps the run red until fixed:

| Step | Mismatch | Ticket |
|---|---|---|
| `get missing appointment` | Java 403 `You have no role at this appointment`, Rust 404 `Appointment not found` (plain text) | #157 |
| `delete appointment` (after `cancel`) | Java 200, Rust 404 | #159 |

### All mismatches and their tickets

| # | Where | Mismatch | Ticket |
|---|---|---|---|
| 1 | Rust | Pod never Ready: event-bus listeners take the whole DB pool | #153 (fixed) |
| 2 | Rust | `GET /appointments`: Java `{data, meta}` paged, Rust bare array; Java objects carry `participants`, `messages`, `group_participants` (list, PATCH, admin list), Rust not | #156 |
| 3 | Rust | Error bodies `text/plain` (or empty) instead of the JSON `ErrorResponse`; missing appointment 404 instead of 403 | #157 |
| 4 | Rust | Invited participant (PENDING) gets 403 on `GET /appointments/{id}`; a non-friend can be added as participant (Java 403); creator starts `APPROVED` (Java `PENDING`), so the creator's RSVP fails with 400 | #158 |
| 5 | Rust | No system messages on RSVP/cancel; `DELETE` after `cancel` 404; group members are not turned into participants (admin change status 400); `POST /admin/appointments/message` 500 | #159 |
| 6 | Rust | `GET /friendships/suggestions` always `[]` (Java returns known users) | #160 |
| 7 | Rust | `checks[].data` missing in readiness, trailing slash 404, unknown route has no body, malformed settings JSON 500 (Java 400), push subscribe error without body | #161 |
| 8 | Java | The 8 participation routes of `AppointmentParticipationResource` answer 404 in the packaged app (no class-level `@Path` since #54) | #162 |
| 9 | Java | 500 instead of 401/400/404 for: request without token, missing/invalid fields and enums, unknown admin user, wrong method, unlinking an unknown provider | #162 |

Mismatches 8 and 9 are Java defects; Rust returns the sensible status. They are allowlisted (9) or worked around (8). Whether Rust should copy them is the decision asked for in #162.

### The Java participation routes (mismatch 8)

On the unmodified Java build, `POST /api/v2/appointments/{id}/participants/{userId}` and the seven other routes of the class return `404 Unable to find matching target resource method` for every user, so the harness could not compare any participation endpoint (every step "java 404, rust 200"). To still test Rust against the *intended* Java behaviour, the Java image of the final runs (`chronos-backend:parity-patched`) is the same build with one local change that was **not committed** as a source change; it is stored as `deployment/parity/java-participation-routes.patch` (apply with `git apply`, tag `chronos-backend:parity-patched`, the tag in README and `values-parity.yaml`): class-level `@Path("/api/v2/appointments")` on `AppointmentParticipationResource` and the method paths made relative (only that file). The harness results are therefore against "Java as intended"; without the local change all participation steps differ. The fix belongs to the Java owner (#162); "do not change Java code" holds for this repository.

## Explorative pass (`deployment/parity/explore.py`)

103 steps with two users (and the admin account as non-friend) beyond the contract scenario: friendship request/accept/decline/end, group membership and ownership, appointment create/update/validation/permissions as owner, invitee and stranger, RSVP and role changes, messages, push subscribe error paths, admin paging edge cases, trailing slash, unknown route, wrong method, invalid token. 42 steps differed; apart from the cascades of the items in the table above:

- `PATCH /api/v2/user` with empty names: Java 500, Rust 200 (Java defect, #162).
- `PUT /api/v2/settings` with `[1,`: Java 400, Rust 500 (#161).
- `GET /api/v2/groups/` (trailing slash): Java 200, Rust 404 (#161).
- Participant lists differ in length after a non-friend was added (#158) and messages differ by the system messages (#159).

Identical on both sides (checked): duplicate friendship request 400, request to self 400, accept as the sender 403, accept twice 400, group create with empty name 400, foreign group patch/member add/list 403, non-numeric id 404, stranger delete/role change 403, push subscribe/unsubscribe of foreign endpoints, admin paging (`size=0`, negative page, huge size, non-numeric page 404), non-admin 403, anonymous 401, invalid token 401.

## Coverage limits (what this run does not prove)

- Steps that fail the same way on both sides without a success path: `unlink unknown provider` (500/500), `create group without body` (500/500), `patch user` with invalid e-mail (500/500), `settings invalid body` (500/500). Their success bodies are not compared.
- Several arrays are empty on at least one side and their element shape is unchecked (`UNCHECKED` lines of the run): `linked accounts`, `passkeys`, `incoming requests`, `admin push log`, ...
- For success bodies only status, media type and JSON shape are compared, not values (ADR-2026-10-20-021); e.g. the creator's participation status (#158) is only visible through the RSVP step.
- No browser flow (link/passkey redirect URLs are only compared in shape), no Web Push delivery (VAPID keys are throw-away), no scheduler/reminder behaviour, no multi-replica leader election, no load or performance comparison.
- The frontend was not run against Rust. Its store calls `/api/v2/appointments/{id}/participants/users...` and `/participants/groups...`, which neither backend serves.

## Open at the end of this entry

- **Acceptance criterion 2 ("only allowlist entries") is not met**: the final run still exits 1 with 2 mismatches, both `missing_route`, which the allowlist refuses by design (ADR-2026-10-20-021). They block the run until their tickets are fixed:
  - `get missing appointment` (Java 403, Rust 404): #157. #157 was merged to `main` as `5960878` (PR #165) *after* the Rust image of the final run (built from `5b6dd8f`), so the run does not contain it. The next run on an image from `5960878` or later should clear this step.
  - `delete appointment` after `cancel` (Java 200, Rust 404): #159, open.
- The re-run on a Rust image built after #153 without `DATABASE_MAX_CONNECTIONS` is done (below); the workaround was removed from `values-parity.yaml`. `patch user` is gone from the mismatches (Keycloak reset, realm user with the target e-mail).
- The mismatches #156-#162 are open; the allowlist entries are removed with their tickets. When #159 (and #157 on the image) are in, rebuild the Rust image (`docker build`, import, pin, `rollout restart`), reset (`reset-databases.sh`) and run `run-contract.sh` again; the expected result is the output below without the two `MISMATCH` blocks.

## Final harness run (run 6)

2026-10-20, 15:10-15:45 UTC, after the node had recovered from DiskPressure. Rust image built from this checkout (base `main` `5b6dd8f`, contains #153; `DATABASE_MAX_CONNECTIONS` removed, the pod env has no such variable), Java image `chronos-backend:parity-patched` (see "The Java participation routes"), `reset-databases.sh` before the run (fresh databases, Keycloak re-imported). Commands:

```bash
docker build -t chronos-rust-backend:parity rust-backend && docker save chronos-rust-backend:parity | sudo k3s ctr -n k8s.io images import -
sudo k3s ctr -n k8s.io images label docker.io/library/chronos-rust-backend:parity io.cri-containerd.pinned=pinned
helm upgrade --install chronos deployment -n chronos-parity -f deployment/values-local.yaml -f deployment/values-parity.yaml \
  --set-file secrets.values.vapid_public_key=... --set-file secrets.values.vapid_private_key=...
deployment/parity/reset-databases.sh
kubectl -n chronos-parity port-forward svc/chronos-rust-backend 18081:80 &  curl localhost:18081/q/health/ready
deployment/parity/run-contract.sh        # exit 1
```

All four pods Ready (`chronos-backend`, `chronos-rust-backend`, `keycloak`, `postgres`); `GET /q/health/ready` on the Rust service answered `200 {"checks":[{"name":"Database connections health check","status":"UP"}],"status":"UP"}` (acceptance criterion 1). Output, `exit 1`:

```
MISMATCH get missing appointment (GET /api/v2/appointments/{missing_id})
  status: java 403, rust 404
  $  missing_route  java: 403, rust: 404
MISMATCH delete appointment (DELETE /api/v2/appointments/{appt})
  status: java 200, rust 404
  $  missing_route  java: 200, rust: 404
ALLOWED health ready $.checks[[]].data missing_in_rust: Rust omits checks[].data (#161)
ALLOWED unauthenticated request $ status_mismatch: Java answers 500 for a request without token, Rust 401 (#162; Java defect)
ALLOWED create appointment without body fields $ status_mismatch: Java answers 500 (NPE) for missing fields, Rust 422 (#162; Java defect)
ALLOWED rsvp with invalid status $ status_mismatch: Java answers 500 for an unknown enum value, Rust 422 (#162; Java defect)
ALLOWED list appointments $ type_mismatch: Java returns {data, meta}, Rust a bare array (#156)
ALLOWED patch appointment $.*participants missing_in_rust: Rust response lacks participants/group_participants (#156)
ALLOWED patch appointment $.messages missing_in_rust: Rust response lacks messages (#156)
ALLOWED admin list appointments* $.data[[]].* missing_in_rust: Rust appointment objects lack participants/group_participants/messages (#156)
ALLOWED rsvp $ status_mismatch: Java creates the owner as PENDING, Rust as APPROVED (#158)
ALLOWED list messages $ length_mismatch: Rust writes no system messages for RSVP/cancel (#159)
ALLOWED list messages of missing appointment $ content_type_mismatch: Rust answers text/plain instead of the JSON error body (#157)
ALLOWED friend suggestions $ length_mismatch: Rust returns no suggestions (#160)
ALLOWED admin change participation status $ status_mismatch: Rust does not turn group members into participants (#159)
ALLOWED admin send message $ status_mismatch: Rust answers 500, Java 200 (#159)
ALLOWED delete admin appointment $ content_type_mismatch: Rust answers text/plain instead of the JSON error body (#157)
81 steps, 2 mismatches, 18 allowed differences, 6 arrays with unchecked element shape
```

UNCHECKED lines:

```
UNCHECKED health live (GET /q/health/live) $.checks  beide leer, Form ungeprüft
UNCHECKED linked accounts (GET /api/v2/user/linked) $  beide leer, Form ungeprüft
UNCHECKED passkeys (GET /api/v2/user/passkeys) $  beide leer, Form ungeprüft
UNCHECKED friend suggestions (GET /api/v2/friendships/suggestions) $  Rust leer, Form ungeprüft
UNCHECKED incoming requests (GET /api/v2/friendships/requests/incoming) $  beide leer, Form ungeprüft
UNCHECKED admin push log (GET /api/v2/admin/push/log) $  beide leer, Form ungeprüft
```

### Explorative pass on the final state (run 4 of `explore.py`)

`deployment/parity/reset-databases.sh`, then `deployment/parity/run-contract.sh --explore`: 103 steps, 43 with differences, exit 1. The differing steps are the same groups as in the table above (list shapes #156, error bodies and 404/403 #157, non-friend and PENDING invitee #158, system messages and group members #159, suggestions #160, trailing slash, malformed settings JSON and push errors #161, Java 500s #162; in the shape-only `DIFF` lines with equal status codes the differing part is the body shape). New relative to run 3: none except `us: patch empty names` (Java 500, Rust 200, #162). The raw output is kept on the workstation (`~/hl/explore-run4.txt`, `~/hl/contract-run6.txt`), not in the repository.
