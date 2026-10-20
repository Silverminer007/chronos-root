import json
import re
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

from parity.runner import ROLE_USER, HttpTarget
from parity.scenario import SCENARIO

PROVIDED = {"other", "redirect", "provider", "unknown_provider", "missing_user", "missing_id"}


class ScenarioDefinitionTest(unittest.TestCase):
    def test_step_names_are_unique(self):
        names = [s.name for s in SCENARIO]
        self.assertEqual(len(names), len(set(names)))

    def test_every_placeholder_is_provided_or_captured_by_an_earlier_step(self):
        known = set(PROVIDED)
        for step in SCENARIO:
            used = set(re.findall(r"\{(\w+)\}", step.path + json.dumps(step.body)))
            self.assertLessEqual(used, known, f"{step.name} uses unknown {used - known}")
            known |= set(step.capture)


class ScenarioFriendshipTest(unittest.TestCase):
    """Java only lets friends be added to groups and appointments (404 "Ihr seid nicht befreundet")."""

    def test_second_user_accepts_the_friendship_before_anyone_is_added(self):
        names = [s.name for s in SCENARIO]
        accept = next(s for s in SCENARIO if s.role == "user2" and s.path.endswith("/accept"))
        self.assertLess(SCENARIO.index(accept), names.index("add group member"))
        self.assertLess(SCENARIO.index(accept), names.index("add participant"))

    def test_friends_are_listed_while_the_friendship_exists(self):
        names = [s.name for s in SCENARIO]
        self.assertLess(names.index("list friends"), names.index("end friendship"))


class _Handler(BaseHTTPRequestHandler):
    seen = []

    def _reply(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length).decode() if length else None
        _Handler.seen.append((self.command, self.path, self.headers.get("Authorization"), body))
        if self.path == "/empty":
            self.send_response(204)
            self.end_headers()
            return
        payload = json.dumps({"message": "nope"}).encode() if self.path == "/err" else b'{"a":1}'
        self.send_response(404 if self.path == "/err" else 200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    do_GET = do_POST = _reply

    def log_message(self, *args):
        pass


class HttpTargetTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = HTTPServer(("127.0.0.1", 0), _Handler)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.target = HttpTarget(f"http://127.0.0.1:{cls.server.server_port}", {ROLE_USER: "tok"})

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def test_json_response_with_token_and_body(self):
        r = self.target.send("POST", "/x", {"k": 1}, ROLE_USER)
        self.assertEqual((r.status, r.body), (200, {"a": 1}))
        self.assertEqual(_Handler.seen[-1], ("POST", "/x", "Bearer tok", '{"k": 1}'))

    def test_error_status_is_a_response_not_an_exception(self):
        r = self.target.send("GET", "/err", None, ROLE_USER)
        self.assertEqual((r.status, r.body), (404, {"message": "nope"}))

    def test_empty_body_is_none(self):
        self.assertIsNone(self.target.send("GET", "/empty", None, ROLE_USER).body)

    def test_anonymous_sends_no_token(self):
        self.target.send("GET", "/x", None, "anon")
        self.assertIsNone(_Handler.seen[-1][2])

    def test_health_and_metrics_go_to_the_management_url_when_set(self):
        other = HTTPServer(("127.0.0.1", 0), _Handler)
        threading.Thread(target=other.serve_forever, daemon=True).start()
        self.addCleanup(lambda: (other.shutdown(), other.server_close()))
        target = HttpTarget(f"http://127.0.0.1:{self.server.server_port}", {},
                            management_url=f"http://127.0.0.1:{other.server_port}")
        calls = []
        _Handler.seen = calls
        for path in ("/q/health/ready", "/metrics", "/api/v2/groups"):
            target.send("GET", path, None, "anon")
        self.assertEqual([c[1] for c in calls], ["/q/health/ready", "/metrics", "/api/v2/groups"])
        # the first two went to the other server: its port is not the API port
        self.assertEqual(target._base_for("/metrics"), f"http://127.0.0.1:{other.server_port}")
        self.assertEqual(target._base_for("/api/v2/groups"), f"http://127.0.0.1:{self.server.server_port}")

    def test_management_url_unset_keeps_one_base(self):
        self.assertEqual(self.target._base_for("/metrics"), self.target.base_url)

    def test_unreachable_raises_connection_error(self):
        with self.assertRaises(ConnectionError):
            HttpTarget("http://127.0.0.1:1", {}, timeout=1).send("GET", "/", None, ROLE_USER)


if __name__ == "__main__":
    unittest.main()
