import unittest

from parity.compare import Response
from parity.runner import Step, Target, expand, extract, run_scenario, covered_endpoints


class ExpandTest(unittest.TestCase):
    def test_substitutes_in_strings_and_nested_values(self):
        ctx = {"id": 7, "me": "u"}
        self.assertEqual(expand("/a/{id}/b/{me}", ctx), "/a/7/b/u")
        self.assertEqual(expand({"k": ["{id}"], "n": 1}, ctx), {"k": [7], "n": 1})

    def test_whole_placeholder_keeps_type_in_bodies(self):
        self.assertEqual(expand({"group_id": "{id}"}, {"id": 7}), {"group_id": 7})

    def test_unknown_variable_raises(self):
        with self.assertRaises(KeyError):
            expand("/a/{nope}", {})


class ExtractTest(unittest.TestCase):
    def test_dotted_path(self):
        self.assertEqual(extract({"a": {"b": [{"c": 3}]}}, "a.b.0.c"), 3)

    def test_missing_returns_none(self):
        self.assertIsNone(extract({"a": 1}, "b.c"))
        self.assertIsNone(extract(None, "a"))


class FakeTarget(Target):
    def __init__(self, responses):
        self.responses = responses
        self.calls = []

    def send(self, method, path, body, role):
        self.calls.append((method, path, body, role))
        return self.responses.pop(0)


class ScenarioTest(unittest.TestCase):
    def test_captures_are_per_target_and_applied_to_later_steps(self):
        steps = [
            Step("create", "POST", "/things", body={"n": 1}, capture={"id": "id"}),
            Step("read", "GET", "/things/{id}"),
        ]
        java = FakeTarget([Response(200, {"id": 11}, "application/json"), Response(200, {"id": 11}, "application/json")])
        rust = FakeTarget([Response(200, {"id": 99}, "application/json"), Response(200, {"id": 99}, "application/json")])
        results = run_scenario(steps, java, rust, {})
        self.assertEqual(java.calls[1][1], "/things/11")
        self.assertEqual(rust.calls[1][1], "/things/99")
        self.assertEqual([r.diffs for r in results], [[], []])

    def test_failed_capture_falls_back_to_unknown_id_so_the_mismatch_stays_visible(self):
        steps = [
            Step("create", "POST", "/things", capture={"id": "id"}),
            Step("read", "GET", "/things/{id}"),
        ]
        bad = Response(500, None)
        java = FakeTarget([bad, Response(404, None)])
        rust = FakeTarget([bad, Response(404, None)])
        results = run_scenario(steps, java, rust, {})
        self.assertEqual(len(results), 2)
        self.assertEqual(java.calls[1][1], "/things/0")

    def test_whole_body_capture(self):
        steps = [Step("get", "GET", "/s", capture={"s": "$"}), Step("put", "PUT", "/s", body="{s}")]
        body = {"a": "ALL"}
        java = FakeTarget([Response(200, body, "application/json"), Response(204, None)])
        rust = FakeTarget([Response(200, body, "application/json"), Response(204, None)])
        run_scenario(steps, java, rust, {})
        self.assertEqual(java.calls[1][2], body)

    def test_diff_is_recorded(self):
        steps = [Step("s", "GET", "/x")]
        java = FakeTarget([Response(200, {"a": 1}, "application/json")])
        rust = FakeTarget([Response(200, {}, "application/json")])
        (result,) = run_scenario(steps, java, rust, {})
        self.assertEqual([d.kind for d in result.diffs], ["missing_in_rust"])

    def test_covered_endpoints(self):
        steps = [Step("a", "GET", "/x/{id}"), Step("b", "GET", "/y?q=1")]
        self.assertEqual(covered_endpoints(steps), {("GET", "/x/{}"), ("GET", "/y")})


if __name__ == "__main__":
    unittest.main()
