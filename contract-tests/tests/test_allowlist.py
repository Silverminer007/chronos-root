import json
import tempfile
import unittest
from pathlib import Path

from parity.allowlist import Allowlist, AllowlistError
from parity.compare import Diff


def entry(**kw):
    base = {"step": "list groups", "kind": "extra_in_rust", "path": "$.x", "reason": "documented"}
    base.update(kw)
    return base


class AllowlistTest(unittest.TestCase):
    def test_entry_without_reason_is_rejected(self):
        with self.assertRaises(AllowlistError):
            Allowlist.from_entries([entry(reason="")])
        e = entry()
        del e["reason"]
        with self.assertRaises(AllowlistError):
            Allowlist.from_entries([e])

    def test_whitespace_reason_is_rejected(self):
        with self.assertRaises(AllowlistError):
            Allowlist.from_entries([entry(reason="  ")])

    def test_unknown_key_rejected(self):
        with self.assertRaises(AllowlistError):
            Allowlist.from_entries([entry(extra=1)])

    def test_matches_step_kind_and_path(self):
        al = Allowlist.from_entries([entry()])
        self.assertTrue(al.allows("list groups", Diff("$.x", "extra_in_rust", "")))
        self.assertFalse(al.allows("other", Diff("$.x", "extra_in_rust", "")))
        self.assertFalse(al.allows("list groups", Diff("$.y", "extra_in_rust", "")))
        self.assertFalse(al.allows("list groups", Diff("$.x", "missing_in_rust", "")))

    def test_globs_in_step_and_path(self):
        al = Allowlist.from_entries([entry(step="admin *", path="$.items[].*")])
        self.assertTrue(al.allows("admin users", Diff("$.items[].email", "extra_in_rust", "")))

    def test_missing_route_cannot_be_allowlisted(self):
        al = Allowlist.from_entries([entry(kind="missing_route", path="$")])
        self.assertFalse(al.allows("list groups", Diff("$", "missing_route", "")))

    def test_unused_entries_reported(self):
        al = Allowlist.from_entries([entry(), entry(step="never")])
        al.allows("list groups", Diff("$.x", "extra_in_rust", ""))
        self.assertEqual([e.step for e in al.unused()], ["never"])

    def test_load_file(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "a.json"
            p.write_text(json.dumps({"entries": [entry()]}))
            self.assertEqual(len(Allowlist.load(p).entries), 1)

    def test_rust_only_routes_need_a_reason(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "a.json"
            p.write_text(json.dumps({"rust_only": [{"route": "GET /x"}]}))
            with self.assertRaises(AllowlistError):
                Allowlist.load(p)
            p.write_text(json.dumps({"rust_only": [{"route": "GET /x", "reason": "probe"}]}))
            self.assertEqual(Allowlist.load(p).rust_only, {"GET /x": "probe"})

    def test_load_missing_file_is_empty(self):
        self.assertEqual(Allowlist.load(Path("/nonexistent/x.json")).entries, [])


if __name__ == "__main__":
    unittest.main()
