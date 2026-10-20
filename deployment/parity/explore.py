"""Explorative parity run (ticket #137): multi-user flows and error paths beyond the contract scenario.

Uses the harness engine (`contract-tests/parity`) with a second user (role `user2`). Run through
`run-contract.sh --explore`, which forwards the ports and fetches the tokens. Prints one line per
step (`OK`/`DIFF`) and the diff details; exit code 1 on any difference.
"""

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "contract-tests"))

from parity.runner import ROLE_ADMIN, ROLE_ANON, ROLE_USER, HttpTarget, Step, run_scenario  # noqa: E402

USER2 = "user2"
APPT = {
    "name": "Explore Termin",
    "description": "explorativ",
    "start": "2030-02-01T18:00:00Z",
    "end": "2030-02-01T20:00:00Z",
    "venue": "Haus",
    "minimal_attendees": 1,
}


def s(name, method, path, body=None, role=ROLE_USER, capture=None):
    return Step(name, method, path, body=body, role=role, capture=capture or {})


STEPS = [
    # friendship, both sides acting
    s("fr: user1 requests user2", "POST", "/api/v2/friendships/requests?adresse_id={other}"),
    s("fr: duplicate request", "POST", "/api/v2/friendships/requests?adresse_id={other}"),
    s("fr: request to self", "POST", "/api/v2/friendships/requests?adresse_id={me}"),
    s("fr: request without adresse_id", "POST", "/api/v2/friendships/requests"),
    s("fr: user2 incoming", "GET", "/api/v2/friendships/requests/incoming", role=USER2, capture={"req": "0.requestId"}),
    s("fr: user1 cannot accept own request", "POST", "/api/v2/friendships/requests/{req}/accept"),
    s("fr: user2 accepts", "POST", "/api/v2/friendships/requests/{req}/accept", role=USER2),
    s("fr: user2 accepts again", "POST", "/api/v2/friendships/requests/{req}/accept", role=USER2),
    s("fr: user1 friends", "GET", "/api/v2/friendships/friends"),
    s("fr: user2 friends", "GET", "/api/v2/friendships/friends", role=USER2),
    s("fr: suggestions after friendship", "GET", "/api/v2/friendships/suggestions"),
    # groups with two users
    s("gr: create", "POST", "/api/v2/groups", body={"name": "Explore Gruppe"}, capture={"group": "id"}),
    s("gr: create empty name", "POST", "/api/v2/groups", body={"name": ""}),
    s("gr: create without body", "POST", "/api/v2/groups"),
    s("nf: add non-friend to group", "POST", "/api/v2/groups/{group}/user/{third}"),
    s("gr: add member", "POST", "/api/v2/groups/{group}/user/{other}"),
    s("gr: add member twice", "POST", "/api/v2/groups/{group}/user/{other}"),
    s("gr: user2 lists groups", "GET", "/api/v2/groups", role=USER2),
    s("gr: user2 lists members", "GET", "/api/v2/groups/{group}/users", role=USER2),
    s("gr: user2 patches foreign group", "PATCH", "/api/v2/groups/{group}", body={"name": "x"}, role=USER2),
    s("gr: user2 adds member to foreign group", "POST", "/api/v2/groups/{group}/user/{me}", role=USER2),
    s("gr: non-numeric id", "PATCH", "/api/v2/groups/abc", body={"name": "x"}),
    # appointments, owner and participant
    s("ap: create", "POST", "/api/v2/appointments", body=APPT, capture={"appt": "id"}),
    s("ap: end before start", "POST", "/api/v2/appointments", body={**APPT, "end": "2030-02-01T10:00:00Z"}),
    s("ap: missing name", "POST", "/api/v2/appointments", body={k: v for k, v in APPT.items() if k != "name"}),
    s("ap: invalid date", "POST", "/api/v2/appointments", body={**APPT, "start": "morgen"}),
    s("ap: negative minimal_attendees", "POST", "/api/v2/appointments", body={**APPT, "minimal_attendees": -1}),
    s("ap: malformed json", "POST", "/api/v2/appointments", body="{not json"),
    s("ap: user2 get before invite", "GET", "/api/v2/appointments/{appt}", role=USER2),
    s("ap: user2 list before invite", "GET", "/api/v2/appointments", role=USER2),
    s("nf: add non-friend as participant", "POST", "/api/v2/appointments/{appt}/participants/{third}", body={"user_role": "ATTENDANT"}),
    s("ap: add participant", "POST", "/api/v2/appointments/{appt}/participants/{other}", body={"user_role": "ATTENDANT"}),
    s("ap: add participant twice", "POST", "/api/v2/appointments/{appt}/participants/{other}", body={"user_role": "ATTENDANT"}),
    s("ap: add participant invalid role", "POST", "/api/v2/appointments/{appt}/participants/{other}", body={"user_role": "KING"}),
    s("ap: user2 get after invite", "GET", "/api/v2/appointments/{appt}", role=USER2),
    s("ap: user2 list after invite", "GET", "/api/v2/appointments", role=USER2),
    s("ap: user2 patch", "PATCH", "/api/v2/appointments/{appt}", body={**APPT, "venue": "x"}, role=USER2),
    s("ap: user2 delete", "DELETE", "/api/v2/appointments/{appt}", role=USER2),
    s("ap: user2 change role", "PUT", "/api/v2/appointments/{appt}/participation/{me}/role", body={"role": "HELPER"}, role=USER2),
    s("ap: user2 participants", "GET", "/api/v2/appointments/{appt}/participants", role=USER2),
    s("ap: user2 rsvp approved", "POST", "/api/v2/appointments/{appt}/participation", body={"status": "APPROVED"}, role=USER2),
    s("ap: user2 rsvp declined", "POST", "/api/v2/appointments/{appt}/participation", body={"status": "DECLINED"}, role=USER2),
    s("ap: user2 rsvp without body", "POST", "/api/v2/appointments/{appt}/participation", role=USER2),
    s("ap: user2 status", "GET", "/api/v2/appointments/{appt}/participation/status", role=USER2),
    s("ap: owner participants", "GET", "/api/v2/appointments/{appt}/participants"),
    s("ap: owner changes user2 role", "PUT", "/api/v2/appointments/{appt}/participation/{other}/role", body={"role": "HELPER"}),
    s("ap: owner changes role of non-participant", "PUT", "/api/v2/appointments/{appt}/participation/{missing_user}/role", body={"role": "HELPER"}),
    s("ap: owner changes role invalid", "PUT", "/api/v2/appointments/{appt}/participation/{other}/role", body={"role": "KING"}),
    s("ap: add group", "POST", "/api/v2/appointments/{appt}/groups/{group}", body={"user_role": "ATTENDANT"}),
    s("ap: add missing group", "POST", "/api/v2/appointments/{appt}/groups/{missing_id}", body={"user_role": "ATTENDANT"}),
    s("ap: participants with group", "GET", "/api/v2/appointments/{appt}/participants"),
    s("ap: remove group", "DELETE", "/api/v2/appointments/{appt}/groups/{group}"),
    s("ap: patch moves appointment", "PATCH", "/api/v2/appointments/{appt}", body={**APPT, "start": "2030-02-02T18:00:00Z", "end": "2030-02-02T20:00:00Z"}),
    s("ap: patch missing", "PATCH", "/api/v2/appointments/{missing_id}", body=APPT),
    s("ap: delete missing", "DELETE", "/api/v2/appointments/{missing_id}"),
    # messages
    s("msg: user2 sends", "POST", "/api/v2/appointments/{appt}/messages", body={"body": "Hallo"}, role=USER2),
    s("msg: empty body", "POST", "/api/v2/appointments/{appt}/messages", body={"body": ""}),
    s("msg: without body", "POST", "/api/v2/appointments/{appt}/messages"),
    s("msg: owner lists", "GET", "/api/v2/appointments/{appt}/messages"),
    s("msg: user2 lists", "GET", "/api/v2/appointments/{appt}/messages", role=USER2),
    s("msg: list with paging", "GET", "/api/v2/appointments/{appt}/messages?page=0&size=1"),
    # removal and deletion
    s("ap: remove participant", "DELETE", "/api/v2/appointments/{appt}/participants/{other}"),
    s("ap: user2 get after removal", "GET", "/api/v2/appointments/{appt}", role=USER2),
    s("ap: remove participant again", "DELETE", "/api/v2/appointments/{appt}/participants/{other}"),
    s("ap: owner delete", "DELETE", "/api/v2/appointments/{appt}"),
    s("ap: get deleted", "GET", "/api/v2/appointments/{appt}"),
    s("gr: owner deletes group", "DELETE", "/api/v2/groups/{group}"),
    s("gr: delete group again", "DELETE", "/api/v2/groups/{group}"),
    # friendship ends and is requested again (decline path)
    s("fr: user1 ends friendship", "DELETE", "/api/v2/friendships/friends/{other}"),
    s("fr: user1 requests again", "POST", "/api/v2/friendships/requests?adresse_id={other}"),
    s("fr: user2 lists incoming again", "GET", "/api/v2/friendships/requests/incoming", role=USER2, capture={"req2": "0.requestId"}),
    s("fr: user2 declines request", "POST", "/api/v2/friendships/requests/{req2}/decline", role=USER2),
    # user and settings
    s("us: patch invalid email", "PATCH", "/api/v2/user?redirectUri={redirect}", body={"first_name": "A", "last_name": "B", "email": "kein-email"}),
    s("us: patch empty names", "PATCH", "/api/v2/user?redirectUri={redirect}", body={"first_name": "", "last_name": "", "email": "x@example.org"}),
    s("us: patch without redirectUri", "PATCH", "/api/v2/user", body={"first_name": "A", "last_name": "B", "email": "x@example.org"}),
    s("us: user2 get", "GET", "/api/v2/user", role=USER2),
    s("us: settings invalid body", "PUT", "/api/v2/settings", body={"unknown": 1}),
    s("us: settings malformed", "PUT", "/api/v2/settings", body="[1,"),
    # push
    s("push: subscribe without keys", "POST", "/api/v2/push/subscribe", body={"endpoint": "https://push.example.org/x"}),
    s("push: subscribe empty", "POST", "/api/v2/push/subscribe", body={}),
    s("push: subscribe http endpoint", "POST", "/api/v2/push/subscribe", body={"endpoint": "http://push.example.org/x", "keys": {"p256dh": "a", "auth": "b"}}),
    s("push: subscribe", "POST", "/api/v2/push/subscribe", body={"endpoint": "https://push.example.org/e1", "keys": {"p256dh": "a", "auth": "b"}}),
    s("push: subscribe twice", "POST", "/api/v2/push/subscribe", body={"endpoint": "https://push.example.org/e1", "keys": {"p256dh": "c", "auth": "d"}}),
    s("push: user2 status of foreign endpoint", "GET", "/api/v2/push/status?endpoint=https://push.example.org/e1", role=USER2),
    s("push: user2 unsubscribes foreign endpoint", "DELETE", "/api/v2/push/unsubscribe?endpoint=https://push.example.org/e1", role=USER2),
    s("push: status without endpoint", "GET", "/api/v2/push/status"),
    s("push: unsubscribe unknown", "DELETE", "/api/v2/push/unsubscribe?endpoint=https://push.example.org/none"),
    s("push: unsubscribe", "DELETE", "/api/v2/push/unsubscribe?endpoint=https://push.example.org/e1"),
    # admin
    s("admin: user2 stats", "GET", "/api/v2/admin/stats", role=USER2),
    s("admin: anonymous stats", "GET", "/api/v2/admin/stats", role=ROLE_ANON),
    s("admin: users page 1", "GET", "/api/v2/admin/users?page=1&size=2", role=ROLE_ADMIN),
    s("admin: users size 0", "GET", "/api/v2/admin/users?page=0&size=0", role=ROLE_ADMIN),
    s("admin: users negative page", "GET", "/api/v2/admin/users?page=-1&size=5", role=ROLE_ADMIN),
    s("admin: users huge size", "GET", "/api/v2/admin/users?page=0&size=100000", role=ROLE_ADMIN),
    s("admin: users non-numeric page", "GET", "/api/v2/admin/users?page=x&size=5", role=ROLE_ADMIN),
    s("admin: users default paging", "GET", "/api/v2/admin/users", role=ROLE_ADMIN),
    s("admin: unknown user", "GET", "/api/v2/admin/users/{missing_user}", role=ROLE_ADMIN),
    s("admin: appointments default paging", "GET", "/api/v2/admin/appointments", role=ROLE_ADMIN),
    s("admin: push log", "GET", "/api/v2/admin/push/log", role=ROLE_ADMIN),
    # generic HTTP behaviour
    s("http: unknown route", "GET", "/api/v2/does-not-exist"),
    s("http: wrong method", "PUT", "/api/v2/groups"),
    s("http: trailing slash", "GET", "/api/v2/groups/"),
    s("http: invalid token", "GET", "/api/v2/groups", role="badtoken"),
]


def main() -> int:
    tokens = {
        ROLE_USER: os.environ["CONTRACT_USER_TOKEN"],
        USER2: os.environ["EXPLORE_USER2_TOKEN"],
        ROLE_ADMIN: os.environ["CONTRACT_ADMIN_TOKEN"],
        "badtoken": "not-a-jwt",
    }
    java = HttpTarget(os.environ["CONTRACT_JAVA_URL"], tokens)
    rust = HttpTarget(os.environ["CONTRACT_RUST_URL"], tokens)
    variables = {
        "other": os.environ.get("CONTRACT_OTHER_USER_ID", "parity-user-2"),
        "me": os.environ.get("EXPLORE_USER1_ID", "parity-user-1"),
        "redirect": "https://chronos.example.org/",
        "third": os.environ.get("EXPLORE_THIRD_USER_ID", "parity-admin"),
        "missing_user": "does-not-exist",
        "missing_id": 999999999,
    }
    results = run_scenario(STEPS, java, rust, variables)
    different = 0
    for result in results:
        status = f"java {result.java.status} rust {result.rust.status}"
        if result.diffs:
            different += 1
            print(f"DIFF {result.step.name} ({result.step.method} {result.step.path}) {status}")
            for diff in result.diffs:
                print(f"     {diff.path} {diff.kind} {diff.detail}")
        else:
            print(f"OK   {result.step.name} {status}")
    print(f"{len(results)} steps, {different} with differences")
    return 1 if different else 0


if __name__ == "__main__":
    sys.exit(main())
