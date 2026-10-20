"""Request definitions: one scenario that touches every endpoint of the Java backend.

The list is written against the Java resources (`backend/.../presentation`), not against what Rust
serves today. `--coverage` checks it against the sources. Steps run in order and share captured
variables per backend (ids are generated, so each backend uses its own).

Variables supplied by the caller: `other` (oidc id of a second user), `redirect` (redirect URI),
`provider`, `unknown_provider`, `missing_user`, `missing_id` (see `cli.main`).
Captured: `me`, `settings`, `group`, `appt`, `req`, `agroup`, `aappt`.
"""

from .runner import ROLE_ADMIN, ROLE_ANON, ROLE_USER2, Step

APPOINTMENT = {
    "name": "Parity Termin",
    "description": "Vertragstest",
    "start": "2030-01-01T18:00:00Z",
    "end": "2030-01-01T20:00:00Z",
    "venue": "Gemeindehaus",
    "minimal_attendees": 2,
}
PUSH_ENDPOINT = "https://push.example.org/parity"
PUSH_SUBSCRIPTION = {"endpoint": PUSH_ENDPOINT, "keys": {"p256dh": "parity-p256dh", "auth": "parity-auth"}}
MISSING_ID = 999999999  # also passed to the run as `{missing_id}`


def _admin(name, method, path, body=None, capture=None):
    return Step(name, method, path, body=body, role=ROLE_ADMIN, capture=capture or {})


SCENARIO = [
    # health and metrics (public)
    Step("health live", "GET", "/q/health/live", role=ROLE_ANON),
    Step("health ready", "GET", "/q/health/ready", role=ROLE_ANON),
    Step("metrics", "GET", "/metrics", role=ROLE_ANON),
    Step("unauthenticated request", "GET", "/api/v2/groups", role=ROLE_ANON),
    # user and settings
    Step("get user", "GET", "/api/v2/user", capture={"me": "id"}),
    Step("post user", "POST", "/api/v2/user"),
    Step("patch user", "PATCH", "/api/v2/user?redirectUri={redirect}", body={"first_name": "Parity", "last_name": "Test", "email": "parity@example.org"}),
    Step("delete user is not allowed", "DELETE", "/api/v2/user"),
    Step("linked accounts", "GET", "/api/v2/user/linked"),
    Step("unlink unknown provider", "DELETE", "/api/v2/user/linked/{unknown_provider}"),
    Step("link url", "GET", "/api/v2/user/link/{provider}?redirectUri={redirect}"),
    Step("passkey registration url", "GET", "/api/v2/user/link/passkey?redirectUri={redirect}"),
    Step("passkey deletion url", "GET", "/api/v2/user/unlink/passkey?redirectUri={redirect}"),
    Step("passkeys", "GET", "/api/v2/user/passkeys"),
    Step("change password url", "GET", "/api/v2/user/change-password?redirectUri={redirect}"),
    Step("get settings", "GET", "/api/v2/settings", capture={"settings": "$"}),
    Step("put settings", "PUT", "/api/v2/settings", body="{settings}"),
    # befriend the second user: Java only adds friends to groups and appointments ("Ihr seid nicht befreundet")
    Step("befriend: send request", "POST", "/api/v2/friendships/requests?adresse_id={other}"),
    Step("befriend: second user lists incoming", "GET", "/api/v2/friendships/requests/incoming", role=ROLE_USER2, capture={"freq": "0.requestId"}),
    Step("befriend: second user accepts", "POST", "/api/v2/friendships/requests/{freq}/accept", role=ROLE_USER2),
    # groups
    Step("create group", "POST", "/api/v2/groups", body={"name": "Parity Gruppe"}, capture={"group": "id"}),
    Step("list groups", "GET", "/api/v2/groups"),
    Step("patch group", "PATCH", "/api/v2/groups/{group}", body={"name": "Parity Gruppe 2"}),
    Step("patch missing group", "PATCH", "/api/v2/groups/{missing_id}", body={"name": "x"}),
    Step("add group member", "POST", "/api/v2/groups/{group}/user/{other}"),
    Step("list group users", "GET", "/api/v2/groups/{group}/users"),
    Step("list users of missing group", "GET", "/api/v2/groups/{missing_id}/users"),
    Step("remove group member", "DELETE", "/api/v2/groups/{group}/user/{other}"),
    # appointments
    Step("create appointment", "POST", "/api/v2/appointments", body=APPOINTMENT, capture={"appt": "id"}),
    Step("create appointment without body fields", "POST", "/api/v2/appointments", body={}),
    Step("list appointments", "GET", "/api/v2/appointments"),
    Step("get appointment", "GET", "/api/v2/appointments/{appt}"),
    Step("get missing appointment", "GET", "/api/v2/appointments/{missing_id}"),
    Step("patch appointment", "PATCH", "/api/v2/appointments/{appt}", body={**APPOINTMENT, "venue": "Kirche"}),
    # participation
    Step("add participant", "POST", "/api/v2/appointments/{appt}/participants/{other}", body={"user_role": "ATTENDANT"}),
    Step("list participants", "GET", "/api/v2/appointments/{appt}/participants"),
    Step("change participation role", "PUT", "/api/v2/appointments/{appt}/participation/{other}/role", body={"role": "HELPER"}),
    Step("rsvp", "POST", "/api/v2/appointments/{appt}/participation", body={"status": "APPROVED"}),
    Step("rsvp with invalid status", "POST", "/api/v2/appointments/{appt}/participation", body={"status": "MAYBE"}),
    Step("participation status", "GET", "/api/v2/appointments/{appt}/participation/status"),
    Step("remove participant", "DELETE", "/api/v2/appointments/{appt}/participants/{other}"),
    Step("add group to appointment", "POST", "/api/v2/appointments/{appt}/groups/{group}", body={"user_role": "ATTENDANT"}),
    Step("remove group from appointment", "DELETE", "/api/v2/appointments/{appt}/groups/{group}"),
    # messages
    Step("send message", "POST", "/api/v2/appointments/{appt}/messages", body={"body": "Hallo zusammen"}),
    Step("list messages", "GET", "/api/v2/appointments/{appt}/messages"),
    Step("list messages of missing appointment", "GET", "/api/v2/appointments/{missing_id}/messages"),
    # friendships
    Step("friend suggestions", "GET", "/api/v2/friendships/suggestions"),
    Step("list friends", "GET", "/api/v2/friendships/friends"),
    Step("end friendship", "DELETE", "/api/v2/friendships/friends/{other}"),
    Step("end friendship twice", "DELETE", "/api/v2/friendships/friends/{other}"),
    Step("send friendship request", "POST", "/api/v2/friendships/requests?adresse_id={other}"),
    Step("outgoing requests", "GET", "/api/v2/friendships/requests/outgoing", capture={"req": "0.requestId"}),
    Step("incoming requests", "GET", "/api/v2/friendships/requests/incoming"),
    Step("accept own request", "POST", "/api/v2/friendships/requests/{req}/accept"),
    Step("decline own request", "POST", "/api/v2/friendships/requests/{req}/decline"),
    Step("cancel request", "DELETE", "/api/v2/friendships/requests/{req}"),
    Step("accept missing request", "POST", "/api/v2/friendships/requests/{missing_id}/accept"),
    # push
    Step("push public key", "GET", "/api/v2/push/public-key"),
    Step("push subscribe", "POST", "/api/v2/push/subscribe", body=PUSH_SUBSCRIPTION),
    Step("push status", "GET", f"/api/v2/push/status?endpoint={PUSH_ENDPOINT}"),
    Step("push unsubscribe", "DELETE", f"/api/v2/push/unsubscribe?endpoint={PUSH_ENDPOINT}"),
    # admin
    Step("admin endpoint as plain user", "GET", "/api/v2/admin/stats"),
    _admin("admin stats", "GET", "/api/v2/admin/stats"),
    _admin("admin list users", "GET", "/api/v2/admin/users?page=0&size=5"),
    _admin("admin get user", "GET", "/api/v2/admin/users/{me}"),
    _admin("admin delete missing user", "DELETE", "/api/v2/admin/users/{missing_user}"),
    _admin("admin create group", "POST", "/api/v2/admin/groups", {"ownerId": "{me}", "groupName": "Admin Gruppe"}, {"agroup": "$"}),
    _admin("admin add group member", "POST", "/api/v2/admin/groups/{agroup}/users/{other}"),
    _admin("admin befriend", "POST", "/api/v2/admin/friendship/befriend", {"userIds": ["{me}", "{other}"]}),
    _admin("admin create appointment", "POST", "/api/v2/admin/appointments", APPOINTMENT, {"aappt": "id"}),
    _admin("admin add group participant", "POST", "/api/v2/admin/appointments/{aappt}/participants/groups", {"group_id": "{agroup}", "user_role": "ATTENDANT"}),
    _admin("admin change participation status", "POST", "/api/v2/admin/appointments/participants/status", {"userId": "{me}", "appointmentId": "{aappt}", "participationStatus": "APPROVED"}),
    _admin("admin send message", "POST", "/api/v2/admin/appointments/message", {"appointment_id": "{aappt}", "sender_id": "{me}", "body": "Admin Nachricht", "timestamp": "2030-01-01T00:00:00Z"}),
    _admin("admin list appointments", "GET", "/api/v2/admin/appointments?page=0&size=5"),
    _admin("admin push test", "POST", "/api/v2/admin/push/test/{me}", "Test"),
    _admin("admin push log", "GET", "/api/v2/admin/push/log"),
    # cleanup (also exercises the delete/cancel endpoints)
    Step("cancel appointment", "POST", "/api/v2/appointments/{appt}/cancel"),
    Step("delete appointment", "DELETE", "/api/v2/appointments/{appt}"),
    Step("delete admin appointment", "DELETE", "/api/v2/appointments/{aappt}"),
    Step("delete group", "DELETE", "/api/v2/groups/{group}"),
    Step("delete admin group", "DELETE", "/api/v2/groups/{agroup}"),
]
