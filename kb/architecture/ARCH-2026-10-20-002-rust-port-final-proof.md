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
- **Explorative pass: exit 0, 106 steps, 0 differences, 11 allowed differences, all `java-defect (#162)`.** The first run on the image of `2b7bb9b` was red with 7 steps (error bodies of the appointments module); review of MR !173 asked for the fix instead of a follow-up ticket, it is done in `rust-backend/` (section "Appointment error bodies") and the pass was repeated on the rebuilt image.
- Rust `GET /q/health/ready` answers 200 `{"checks":[{"data":{"<default>":"UP"},"name":"Database connections health check","status":"UP"}],"status":"UP"}`; all four pods Ready.
- Nothing was deployed outside the namespace `chronos-parity`; the Rust backend is not in staging or production (#140).

The first rerun (images from `main` `f7ab930`) found real deviations that the earlier allowlist could not show: names resolved in PATCH/admin list (Java does not), no move message, PATCH messages, bare 400 for malformed settings (ADR-2026-10-20-029). They are fixed in the final image.

## Environment

| Item | Value |
|---|---|
| Date | 2026-10-20, 19:15-20:35 UTC |
| Commit under test | `main` `f7ab930` (all of #156-#161 merged) plus this change: `2b7bb9b` (Rust fixes of ADR-2026-10-20-029) and the appointment error body fixes on top of `1e9106b` (review of !173) |
| Rust image | `chronos-rust-backend:parity`, built from `1e9106b` plus the error body fixes (working tree, 2026-10-20 ~21:05 UTC), docker image id `sha256:683a8e3bc804ef4cb56c3e35bfd870f5b4648dd0d422c154a473e16233f7d266` (imageID in the pod), containerd manifest `sha256:63d453c9291f8acf0ff5555ab97617c76dcb77e840bfeda70c7f007e32e8903f`, pinned. The earlier image (`2b7bb9b`, id `sha256:7ec2d6ad...`) produced the red explorative pass of 7 steps |
| Java image | `chronos-backend:parity-patched`, built from the same checkout (`backend/` unchanged) with `deployment/parity/java-participation-routes.patch` applied for the build and reverted afterwards, docker image id `sha256:fd3b6a7fb2567ee0381e8e21c4d173925f512fc03066c2752571af54d7fa6cc0`, containerd manifest `sha256:fd24d2a1020db626e9b82bb27e4342adfdb193c316e2ad566127b139748b7638`, pinned |
| Cluster | k3s single node, namespace `chronos-parity` (Helm release `chronos`, unchanged; Keycloak 26.0.7 dev mode, PostgreSQL 16) |

Intermediate runs on earlier Rust images (`f7ab930`: harness 2 mismatches; `0a882de`: harness exit 0 with 90 steps, explore 23 differing steps; `03ac501`: explore 8 differing steps; `2b7bb9b`: explore 7 differing steps) led to the fixes; the numbers above are from the final image. The Java image is unchanged.

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
deployment/parity/run-contract.sh --explore         # exit 0, 106 steps, 0 differences
# repeated on the final image (error body fixes), same commands: fresh databases, explore, fresh databases, harness
deployment/parity/reset-databases.sh && deployment/parity/run-contract.sh --explore   # exit 0
deployment/parity/reset-databases.sh && deployment/parity/run-contract.sh             # exit 0, 90 steps, 0 mismatches
(cd rust-backend && cargo fmt --check && cargo clippy --all-targets && cargo test --no-fail-fast)   # all green
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

## Explorative pass (106 steps, exit 0)

`explore.py` applies the allowlist, has 3 steps with data (`list/get embeds participants, messages and group`, `user2 get embeds`) and 103 + 3 = 106 steps. On the final image all 106 steps are identical or an allowed Java defect (11 entries):

```
ALLOWED ap: missing name ... http: wrong method   (11 lines, all java-defect (#162), see the table above)
106 steps, 0 with differences
```

### Appointment error bodies (fixed after review of !173)

The run on the image of `2b7bb9b` had 7 steps that differed, all in the appointments module. They had two causes, both fixed in `rust-backend/src/appointments/` (the Java `ValidationException` / `BadRequestException` / `ForbiddenException` distinction was collapsed into `BAD_REQUEST` / a wrong 403 text):

1. **`VALIDATION_ERROR` instead of `BAD_REQUEST`, no `fieldErrors`** (`ap: end before start`, `negative minimal_attendees`, `add participant twice`, `owner changes role of non-participant`, `remove participant again`). `ServiceError` now keeps the three Java kinds apart: `ValidationError(msg)` (Java `ValidationException(message)`: `VALIDATION_ERROR`, `fieldErrors: {}`), `FieldValidation{field,message}` (Java `ValidationException(field, error)`: message `Validierungsfehler bei Feld: <field>`, `fieldErrors {field: message}`) and `BadRequest(msg)` (Java `BadRequestException`: `BAD_REQUEST`). Every site of the module was reviewed against Java: name/start-end/minimal_attendees of create and update are `FieldValidation` with the Java texts (`Name cannot be blank`, `End date cannot be before start date` on create, `Start date cannot be after end date` on update, `Minimal attendees must be positive`); "already a participant", "not a participant", "already your status/role", group not linked are `ValidationError`; `invalid role`, `back to pending` and unparsable instants are `BadRequest`; the three "only RESPONSIBLE may add groups / add / remove participants" checks were 400 in Rust but are `ForbiddenException` (403, `You have to be responsible for this appointment`) in Java and are fixed as well.
2. **Message texts** (`remove participant again`: `This user is not a participant of this event` instead of `Participant not found`; `get deleted`: `appointment mit ID <id> wurde nicht gefunden` for GET and PATCH of a participant's deleted appointment via `AppointmentError::NotFoundId`; `add missing group`: Java checks that the actor is a member of the group (`You can only add groups, you're a member of`, 403) before anything else about the group; Rust looked the group up first and answered with the responsibility text. New `AppointmentRepository::is_group_member`, skipped for the admin endpoints like in Java).

New tests in `rust-backend/tests/error_mapping_test.rs` cover the group membership 403, the create validation field errors, the remove-participant text and the not-found text with id; the existing expectations of the participant error code were changed from `BAD_REQUEST` to `VALIDATION_ERROR`, and the group tests make the owner a member of the group like `GroupService#createGroup` does.

## Open

- #162 (Java): the 500 responses of the allowlist, the participation routes without class-level `@Path`, and the move message that always reports 0 hours.
- Not verified against Java (not part of any step of the run): the 404 text of appointment endpoints other than GET and PATCH (delete, cancel, participation status) still says `Appointment not found`.

## Not covered by the contract proof

- **Browser flows**: login, link/unlink provider and passkey redirects (only the shape of the URLs is compared), the frontend's use of the API.
- **Web Push delivery**: VAPID keys are throw-away, the push endpoints are not reachable; only subscribe/status/unsubscribe, the admin push test request and the log shape are compared.
- **Scheduler timing**: reminders, the reminder rules and their timing; no multi-replica leader election.
- **The frontend against Rust**: not run. Its store calls `/api/v2/appointments/{id}/participants/users...` and `/participants/groups...`, which neither backend serves (see ARCH-2026-10-20-001).
- Success bodies are compared by status, media type and JSON shape, not by value (ADR-2026-10-20-021); error bodies by value. No load or performance comparison.
- Arrays that are empty on one side in a step stay `UNCHECKED` (list above).
- **Known deviation, deliberately unchecked: text of the appointment move message.** Rust writes the real number of hours the appointment was moved, Java always writes `0` (ADR-2026-10-20-029 §2). Message bodies in success responses are not compared by value (see above), so no step of the harness or the explorative pass can show this difference and it is not in the allowlist. The claim "the allowlist contains only Java defects" therefore holds for the differences the run can see; this one was decided by reading the code, not proven by a run. It is carried by #162 as a Java defect.
