---
title: ARCH-2026-10-20-002: Final proof run of the Rust port (harness and explorative pass, local k3s)
date: 2026-10-20
component: Rust backend, Deployment, Contract tests
relates_to:
  - ARCH-2026-10-20-001: Parity run of the Rust backend against the Java reference in the local k3s cluster
  - ADR-2026-10-20-029: Final parity run fixes
  - ADR-2026-10-20-024: Java and Rust backend side by side in the local k3s cluster
  - ADR-2026-10-20-021: Contract parity harness for the Rust port
---

## Result

Ticket #169. After the fixes of #156, #158, #159, #160 and #161 both backends were rebuilt from the checkout and compared again on fresh databases.

- **Contract harness: exit 0, 90 steps, 0 mismatches, 3 allowed differences, all `java-defect (#162)`.**
- **The allowlist has no entry that refers to a Rust ticket.** All 14 entries are `java-defect (#162)` (table below).
- **Explorative pass: not clean.** 106 steps, 99 identical or allowed Java defects, **7 steps still differ, all in the error bodies of the appointments module** (below). They are real Rust deviations, not allowlisted, the explorative pass stays red. They need a follow-up ticket (see "Open"); the fixes touch about 20 error sites with mixed Java semantics and are not a small fix.
- Rust `GET /q/health/ready` answers 200 `{"checks":[{"data":{"<default>":"UP"},"name":"Database connections health check","status":"UP"}],"status":"UP"}`; all four pods Ready.
- Nothing was deployed outside the namespace `chronos-parity`; the Rust backend is not in staging or production (#140).

The first rerun (images from `main` `f7ab930`) found real deviations that the earlier allowlist could not show: names resolved in PATCH/admin list (Java does not), no move message, PATCH messages, bare 400 for malformed settings (ADR-2026-10-20-029). They are fixed in the final image.

## Environment

| Item | Value |
|---|---|
| Date | 2026-10-20, 19:15-20:35 UTC |
| Commit under test | `main` `f7ab930` (all of #156-#161 merged) plus this change up to `2b7bb9b` (Rust fixes of ADR-2026-10-20-029) |
| Rust image | `chronos-rust-backend:parity`, built from `2b7bb9b`, docker image id `sha256:7ec2d6ad094e754c0c6d242cf0db1ea24c066a0dd9a8fdd1e2b0cdb213884111` (imageID in the pod), containerd manifest `sha256:c81f1b84907c8f04ce320a8832afeb1b87d34e3a9465177975261a994329e859`, pinned |
| Java image | `chronos-backend:parity-patched`, built from the same checkout (`backend/` unchanged) with `deployment/parity/java-participation-routes.patch` applied for the build and reverted afterwards, docker image id `sha256:fd3b6a7fb2567ee0381e8e21c4d173925f512fc03066c2752571af54d7fa6cc0`, containerd manifest `sha256:fd24d2a1020db626e9b82bb27e4342adfdb193c316e2ad566127b139748b7638`, pinned |
| Cluster | k3s single node, namespace `chronos-parity` (Helm release `chronos`, unchanged; Keycloak 26.0.7 dev mode, PostgreSQL 16) |

Intermediate runs on earlier Rust images (`f7ab930`: harness 2 mismatches; `0a882de`: harness exit 0 with 90 steps, explore 23 differing steps; `03ac501`: explore 8 differing steps) led to the fixes; the numbers above are from the final image.

## Commands

```bash
# Rust image from the checkout, imported and pinned
docker build -t chronos-rust-backend:parity rust-backend
docker save chronos-rust-backend:parity | sudo k3s ctr -n k8s.io images import -
docker rmi chronos-rust-backend:parity
sudo k3s ctr -n k8s.io images label docker.io/library/chronos-rust-backend:parity io.cri-containerd.pinned=pinned
# Java image (patch for the participation routes, #162), Maven in a container, as in deployment/parity/README.md
git apply deployment/parity/java-participation-routes.patch
docker run --rm -v "$PWD/backend":/src -w /src -v maven-repo-parity:/root/.m2 maven:3.9-eclipse-temurin-21 \
  mvn -q -B package -Dmaven.test.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true -Dpmd.skip=true
docker build -f backend/src/main/docker/Dockerfile.jvm -t chronos-backend:parity-patched backend
docker save chronos-backend:parity-patched | sudo k3s ctr -n k8s.io images import -
sudo k3s ctr -n k8s.io images label docker.io/library/chronos-backend:parity-patched io.cri-containerd.pinned=pinned
git apply -R deployment/parity/java-participation-routes.patch
# fresh databases and Keycloak, both backends restarted on the new images (same image tags, pullPolicy Never)
deployment/parity/reset-databases.sh
curl localhost:18091/q/health/ready                 # port-forward svc/chronos-rust-backend: 200
deployment/parity/run-contract.sh                   # exit 0
deployment/parity/reset-databases.sh
deployment/parity/run-contract.sh --explore         # exit 1, 7 steps
```

## Harness output (exit 0)

```
UNCHECKED health live (GET /q/health/live) $.checks  beide leer, Form ungeprüft
UNCHECKED linked accounts (GET /api/v2/user/linked) $  beide leer, Form ungeprüft
UNCHECKED passkeys (GET /api/v2/user/passkeys) $  beide leer, Form ungeprüft
UNCHECKED list appointments (GET /api/v2/appointments) $.data[].group_participants  beide leer, Form ungeprüft
UNCHECKED list appointments (GET /api/v2/appointments) $.data[].messages  beide leer, Form ungeprüft
UNCHECKED patch appointment (PATCH /api/v2/appointments/{appt}) $.group_participants  beide leer, Form ungeprüft
UNCHECKED patch appointment (PATCH /api/v2/appointments/{appt}) $.messages  beide leer, Form ungeprüft
UNCHECKED incoming requests (GET /api/v2/friendships/requests/incoming) $  beide leer, Form ungeprüft
ALLOWED unauthenticated request $ status_mismatch: java-defect (#162): Java answers 500 for a request without token, Rust 401
ALLOWED create appointment without body fields $ status_mismatch: java-defect (#162): Java answers 500 (NPE) for missing fields, Rust 422
ALLOWED rsvp with invalid status $ status_mismatch: java-defect (#162): Java answers 500 for an unknown enum value, Rust 422
90 steps, 0 mismatches, 3 allowed differences, 8 arrays with unchecked element shape
```

(The run also prints 11 `NOTE unused allowlist entry` lines for the explore-only entries.) The 8 `UNCHECKED` lines are the steps that run before the scenario has data (empty on both sides: `health live` checks, `linked accounts`, `passkeys`, the first `list/patch appointment`, `incoming requests`); the same arrays are compared with elements in the new steps `list appointments with embedded data`, `get appointment with embedded data`, `patch appointment with embedded data`, `incoming requests with data` and `admin push log`. `linked accounts` and `passkeys` stay unchecked: the realm has no linked identity provider and no passkey, and a browser flow would be needed.

## Final allowlist (`contract-tests/allowlist.json`)

| Step | Kind | Reason |
|---|---|---|
| `unauthenticated request` | status_mismatch | java-defect (#162): Java answers 500 for a request without token, Rust 401 |
| `create appointment without body fields` | status_mismatch | java-defect (#162): Java answers 500 (NPE) for missing fields, Rust 422 |
| `rsvp with invalid status` | status_mismatch | java-defect (#162): Java answers 500 for an unknown enum value, Rust 422 |
| `ap: missing name` | status_mismatch | java-defect (#162): Java answers 500 (NPE) for a missing name, Rust 400 |
| `ap: invalid date` | status_mismatch | java-defect (#162): Java answers 500 (DateTimeParseException) for an unparsable date, Rust 400 |
| `ap: malformed json` | status_mismatch | java-defect (#162): Java answers 500 for a malformed body, Rust 400 |
| `ap: add participant invalid role` | status_mismatch | java-defect (#162): Java answers 500 for an unknown role, Rust 400 |
| `ap: user2 rsvp declined` | status_mismatch | java-defect (#162): Java answers 500 for the unknown status DECLINED, Rust 400 |
| `ap: user2 rsvp without body` | status_mismatch | java-defect (#162): Java answers 500 for a missing body, Rust 400 |
| `ap: owner changes role invalid` | status_mismatch | java-defect (#162): Java answers 500 for an unknown role, Rust 400 |
| `msg: without body` | status_mismatch | java-defect (#162): Java answers 500 for a missing body, Rust 400 |
| `us: patch empty names` | status_mismatch | java-defect (#162): Java answers 500 for empty names, Rust 200 |
| `admin: unknown user` | status_mismatch | java-defect (#162): Java answers 500 for an unknown user, Rust 404 |
| `http: wrong method` | status_mismatch | java-defect (#162): Java answers 500 for a method the route does not serve, Rust 405 |

`rust_only`: `GET /api/v2/me` (ADR-2026-10-20-017) and `GET /health` (plain-text liveness probe of Rust). Entries 1-3 are used by the harness, entries 4-14 by the explorative pass. The Java defects belong to #162 (Java answers 500 where Rust answers 401/400/404/405/422 or succeeds); the Java move message that always says 0 hours (ADR-2026-10-20-029 §2) is a further one for #162 that does not show in the run.

## Explorative pass (106 steps, exit 1)

`explore.py` now applies the allowlist, has 3 more steps with data (`list/get embeds participants, messages and group`, `user2 get embeds`) and 103 + 3 = 106 steps. 99 steps are identical or an allowed Java defect (11 entries). Remaining differences:

```
DIFF ap: end before start (POST /api/v2/appointments) java 400 rust 400
     $.fieldErrors missing_in_rust java: object
     $.errorCode value_mismatch java: 'VALIDATION_ERROR', rust: 'BAD_REQUEST'
     $.message value_mismatch java: 'Validierungsfehler bei Feld: end', rust: 'end time cannot be before start time'
DIFF ap: negative minimal_attendees (POST /api/v2/appointments) java 400 rust 400
     $.fieldErrors missing_in_rust java: object
     $.errorCode value_mismatch java: 'VALIDATION_ERROR', rust: 'BAD_REQUEST'
     $.message value_mismatch java: 'Validierungsfehler bei Feld: minimal_attendees', rust: 'minimal_attendees must be non-negative'
DIFF ap: add participant twice (POST /api/v2/appointments/{appt}/participants/{other}) java 400 rust 400
     $.fieldErrors missing_in_rust java: object
     $.errorCode value_mismatch java: 'VALIDATION_ERROR', rust: 'BAD_REQUEST'
DIFF ap: owner changes role of non-participant (PUT /api/v2/appointments/{appt}/participation/{missing_user}/role) java 400 rust 400
     $.fieldErrors missing_in_rust java: object
     $.errorCode value_mismatch java: 'VALIDATION_ERROR', rust: 'BAD_REQUEST'
DIFF ap: add missing group (POST /api/v2/appointments/{appt}/groups/{missing_id}) java 403 rust 403
     $.message value_mismatch java: "You can only add groups, you're a member of", rust: 'You have to be responsible for this appointment'
DIFF ap: remove participant again (DELETE /api/v2/appointments/{appt}/participants/{other}) java 400 rust 400
     $.fieldErrors missing_in_rust java: object
     $.errorCode value_mismatch java: 'VALIDATION_ERROR', rust: 'BAD_REQUEST'
     $.message value_mismatch java: 'This user is not a participant of this event', rust: 'Participant not found'
DIFF ap: get deleted (GET /api/v2/appointments/{appt}) java 404 rust 404
     $.message value_mismatch java: 'appointment mit ID 1 wurde nicht gefunden', rust: 'Appointment not found'
106 steps, 7 with differences
```

Groups:

1. **`VALIDATION_ERROR` instead of `BAD_REQUEST` and no `fieldErrors`** (5 steps): Java throws `ValidationException` (code `VALIDATION_ERROR`, body field `fieldErrors`, for field errors the message `Validierungsfehler bei Feld: <field>`); the appointments module of Rust maps all `ServiceError::ValidationError` to `BAD_REQUEST`. Several Rust sites correspond to Java's `BadRequestException` (`invalid role`, `you cannot set your participation status back to pending`) or to a 403, so the mapping needs a per-site review.
2. **Message texts** (3 steps): `This user is not a participant of this event` (remove participant), `You can only add groups, you're a member of` (Java checks the group membership first and answers 403 with that text), `appointment mit ID <id> wurde nicht gefunden` (get of a deleted appointment).

## Open

- **Follow-up ticket needed (could not be created from this run, the ticket helper was not available):** "Rust: appointment error bodies (VALIDATION_ERROR/fieldErrors, Java message texts, not-found text)" with the seven steps above. Until it is done `run-contract.sh --explore` stays red; do not allowlist these.
- #162 (Java): the 500 responses of the allowlist, the participation routes without class-level `@Path`, and the move message that always reports 0 hours.

## Not covered by the contract proof

- **Browser flows**: login, link/unlink provider and passkey redirects (only the shape of the URLs is compared), the frontend's use of the API.
- **Web Push delivery**: VAPID keys are throw-away, the push endpoints are not reachable; only subscribe/status/unsubscribe, the admin push test request and the log shape are compared.
- **Scheduler timing**: reminders, the reminder rules and their timing; no multi-replica leader election.
- **The frontend against Rust**: not run. Its store calls `/api/v2/appointments/{id}/participants/users...` and `/participants/groups...`, which neither backend serves (see ARCH-2026-10-20-001).
- Success bodies are compared by status, media type and JSON shape, not by value (ADR-2026-10-20-021); error bodies by value. No load or performance comparison.
- Arrays that are empty on one side in a step stay `UNCHECKED` (list above).
