import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from parity import cli
from parity.compare import Response
from parity.runner import Step, Target


class Stub(Target):
    def __init__(self, table):
        self.table = table

    def send(self, method, path, body, role):
        return self.table.get((method, path), Response(404, None))


def ok(body):
    return Response(200, body, "application/json")


STEPS = [Step("get a", "GET", "/a"), Step("get b", "GET", "/b")]


def run(argv, java, rust, steps=STEPS):
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        code = cli.main(argv, targets=(java, rust), steps=steps)
    return code, out.getvalue()


class MainTest(unittest.TestCase):
    def test_all_match_exits_zero(self):
        t = {("GET", "/a"): ok({"x": 1}), ("GET", "/b"): ok([])}
        code, out = run([], Stub(t), Stub(t))
        self.assertEqual(code, 0)
        self.assertIn("2 steps, 0 mismatches", out)

    def test_mismatch_exits_one_and_prints_readable_diff(self):
        java = Stub({("GET", "/a"): ok({"x": 1}), ("GET", "/b"): ok([])})
        rust = Stub({("GET", "/a"): ok({"x": "1"}), ("GET", "/b"): ok([])})
        code, out = run([], java, rust)
        self.assertEqual(code, 1)
        self.assertIn("MISMATCH get a (GET /a)", out)
        self.assertIn("$.x", out)
        self.assertIn("type_mismatch", out)

    def test_missing_rust_route_is_a_mismatch(self):
        java = Stub({("GET", "/a"): ok({}), ("GET", "/b"): ok({})})
        rust = Stub({("GET", "/a"): ok({})})
        code, out = run([], java, rust)
        self.assertEqual(code, 1)
        self.assertIn("missing_route", out)

    def _allowlist(self, entries):
        d = tempfile.mkdtemp()
        p = Path(d) / "allow.json"
        p.write_text(json.dumps({"entries": entries}))
        return str(p)

    def test_allowlist_hides_deliberate_difference(self):
        java = Stub({("GET", "/a"): ok({"x": 1}), ("GET", "/b"): ok([])})
        rust = Stub({("GET", "/a"): ok({"x": "1"}), ("GET", "/b"): ok([])})
        al = self._allowlist([{"step": "get a", "kind": "type_mismatch", "path": "$.x", "reason": "Rust serialises as string by design"}])
        code, out = run(["--allowlist", al], java, rust)
        self.assertEqual(code, 0)
        self.assertIn("1 allowed", out)
        self.assertIn("Rust serialises as string by design", out)

    def test_allowlist_cannot_hide_missing_route(self):
        java = Stub({("GET", "/a"): ok({}), ("GET", "/b"): ok({})})
        rust = Stub({("GET", "/a"): ok({})})
        al = self._allowlist([{"step": "get b", "kind": "missing_route", "path": "$", "reason": "later"}])
        code, _ = run(["--allowlist", al], java, rust)
        self.assertEqual(code, 1)

    def test_invalid_allowlist_exits_two(self):
        al = self._allowlist([{"step": "get a", "kind": "type_mismatch", "path": "$.x"}])
        code, out = run(["--allowlist", al], Stub({}), Stub({}))
        self.assertEqual(code, 2)
        self.assertIn("reason", out)

    def test_unreachable_backend_exits_two(self):
        class Down(Target):
            def send(self, *a):
                raise ConnectionError("refused")

        code, out = run([], Down(), Stub({}))
        self.assertEqual(code, 2)
        self.assertIn("refused", out)


class CoverageTest(unittest.TestCase):
    def _tree(self, java_src, rust_src):
        root = Path(tempfile.mkdtemp())
        (root / "java").mkdir()
        (root / "rust").mkdir()
        (root / "java" / "XResource.java").write_text(java_src)
        (root / "rust" / "app.rs").write_text(rust_src)
        return ["--coverage", "--java-src", str(root / "java"), "--rust-src", str(root / "rust")]

    JAVA = '@Path("/a")\npublic class XResource {\n @GET\n public void a() {}\n @POST\n @Path("/{id}")\n public void b() {}\n}'

    def test_full_coverage_exits_zero(self):
        rust = '.route("/a", get(h)).route("/a/:id", post(h)).route("/metrics", get(h))'
        rust += '.route("/q/health/live", get(h)).route("/q/health/ready", get(h))'
        steps = [
            Step("a", "GET", "/a"), Step("b", "POST", "/a/{id}"), Step("m", "GET", "/metrics"),
            Step("l", "GET", "/q/health/live"), Step("r", "GET", "/q/health/ready"),
        ]
        code, out = run(self._tree(self.JAVA, rust), Stub({}), Stub({}), steps)
        self.assertEqual(code, 0, out)

    def test_uncovered_java_endpoint_and_rust_only_route_are_listed(self):
        rust = '.route("/a", get(h)).route("/extra", get(h))'
        steps = [Step("a", "GET", "/a")]
        code, out = run(self._tree(self.JAVA, rust), Stub({}), Stub({}), steps)
        self.assertEqual(code, 1)
        self.assertIn("POST /a/{}", out)
        self.assertIn("GET /extra", out)


if __name__ == "__main__":
    unittest.main()
