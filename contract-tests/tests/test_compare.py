import unittest

from parity.compare import Response, compare_responses, shape_diffs


def resp(status, body=None, ctype="application/json"):
    return Response(status=status, body=body, content_type=ctype)


class ShapeDiffTest(unittest.TestCase):
    def test_equal_ignores_values_and_field_order(self):
        a = {"id": 1, "name": "a", "tags": ["x"]}
        b = {"tags": ["y"], "name": "b", "id": 99}
        self.assertEqual(shape_diffs(a, b), [])

    def test_missing_field_is_reported_with_path(self):
        diffs = shape_diffs({"a": 1, "b": 2}, {"a": 1})
        self.assertEqual([(d.path, d.kind) for d in diffs], [("$.b", "missing_in_rust")])

    def test_extra_field_in_rust(self):
        diffs = shape_diffs({"a": 1}, {"a": 1, "c": 1})
        self.assertEqual([(d.path, d.kind) for d in diffs], [("$.c", "extra_in_rust")])

    def test_null_versus_missing_are_distinct(self):
        self.assertEqual(
            [d.kind for d in shape_diffs({"a": None}, {})], ["missing_in_rust"]
        )
        self.assertEqual(
            [d.kind for d in shape_diffs({"a": None}, {"a": 1})], ["type_mismatch"]
        )

    def test_type_mismatch_names_both_types(self):
        (d,) = shape_diffs({"a": 1}, {"a": "1"})
        self.assertEqual((d.path, d.kind), ("$.a", "type_mismatch"))
        self.assertIn("number", d.detail)
        self.assertIn("string", d.detail)

    def test_bool_is_not_number(self):
        self.assertEqual(len(shape_diffs({"a": True}, {"a": 1})), 1)

    def test_int_and_float_are_both_number(self):
        self.assertEqual(shape_diffs({"a": 1}, {"a": 1.5}), [])

    def test_arrays_compare_element_shapes_in_union(self):
        java = [{"id": 1, "x": None}, {"id": 2, "x": "s"}]
        rust = [{"id": 5, "x": "t"}, {"id": 6, "x": None}]
        self.assertEqual(shape_diffs(java, rust), [])

    def test_array_length_mismatch(self):
        (d,) = shape_diffs([1, 2], [1])
        self.assertEqual((d.path, d.kind), ("$", "length_mismatch"))

    def test_empty_array_skips_element_check(self):
        self.assertEqual(shape_diffs([], []), [])

    def test_nested_paths(self):
        (d,) = shape_diffs({"a": [{"b": {"c": 1}}]}, {"a": [{"b": {}}]})
        self.assertEqual(d.path, "$.a[].b.c")


class CompareResponsesTest(unittest.TestCase):
    def test_identical_ok(self):
        self.assertEqual(compare_responses(resp(200, {"a": 1}), resp(200, {"a": 2})), [])

    def test_status_mismatch(self):
        diffs = compare_responses(resp(200, {}), resp(201, {}))
        self.assertEqual([d.kind for d in diffs], ["status_mismatch"])

    def test_rust_404_where_java_is_not_is_missing_route(self):
        diffs = compare_responses(resp(200, {"a": 1}), resp(404, None))
        self.assertEqual([d.kind for d in diffs], ["missing_route"])

    def test_rust_405_where_java_is_not_is_missing_route(self):
        diffs = compare_responses(resp(200, {}), resp(405, None))
        self.assertEqual([d.kind for d in diffs], ["missing_route"])

    def test_both_404_is_a_plain_match(self):
        self.assertEqual(compare_responses(resp(404, {"m": "x"}), resp(404, {"m": "x"})), [])

    def test_status_mismatch_stops_body_comparison(self):
        diffs = compare_responses(resp(200, {"a": 1}), resp(500, {"b": 1}))
        self.assertEqual(len(diffs), 1)

    def test_error_body_values_are_compared(self):
        diffs = compare_responses(
            resp(400, {"message": "Name fehlt"}), resp(400, {"message": "name missing"})
        )
        self.assertEqual([(d.path, d.kind) for d in diffs], [("$.message", "value_mismatch")])

    def test_error_body_volatile_keys_ignored(self):
        java = resp(404, {"message": "x", "timestamp": "2026-01-01T00:00:00Z"})
        rust = resp(404, {"message": "x", "timestamp": "2026-02-02T00:00:00Z"})
        self.assertEqual(compare_responses(java, rust), [])

    def test_message_body_without_timestamp_in_rust_reported(self):
        diffs = compare_responses(
            resp(200, {"text": "x", "timestamp": "2026-01-01T00:00:00Z"}), resp(200, {"text": "x"})
        )
        self.assertEqual([(d.path, d.kind) for d in diffs], [("$.timestamp", "missing_in_rust")])

    def test_success_body_volatile_key_type_checked(self):
        diffs = compare_responses(resp(200, {"timestamp": "t"}), resp(200, {"timestamp": None}))
        self.assertEqual([(d.path, d.kind) for d in diffs], [("$.timestamp", "type_mismatch")])

    def test_error_body_volatile_key_missing_reported(self):
        diffs = compare_responses(resp(404, {"message": "x", "timestamp": "t"}), resp(404, {"message": "x"}))
        self.assertEqual([d.kind for d in diffs], ["missing_in_rust"])

    def test_error_body_shape_still_checked(self):
        diffs = compare_responses(resp(400, {"message": "x", "code": 1}), resp(400, {"message": "x"}))
        self.assertEqual([d.kind for d in diffs], ["missing_in_rust"])

    def test_empty_vs_json_body(self):
        diffs = compare_responses(resp(200, {"a": 1}), resp(200, None))
        self.assertEqual([d.kind for d in diffs], ["body_presence_mismatch"])

    def test_plain_text_bodies_compared_by_value(self):
        self.assertEqual(
            compare_responses(resp(200, "k1", "text/plain"), resp(200, "k2", "text/plain")),
            [],
        )
        diffs = compare_responses(
            resp(200, "OK", "text/plain; charset=UTF-8"), resp(200, "OK!", "text/plain")
        )
        self.assertEqual(diffs, [])

    def test_content_type_mismatch(self):
        diffs = compare_responses(resp(200, "x", "text/plain"), resp(200, {"a": 1}))
        self.assertEqual([d.kind for d in diffs], ["content_type_mismatch"])


if __name__ == "__main__":
    unittest.main()
