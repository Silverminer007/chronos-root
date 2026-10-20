"""Comparison of one Java response with one Rust response.

Only the contract is compared: status, content type, JSON field names, JSON types
(null is its own type, so null and missing differ) and, for error bodies, the values.
Field order, generated ids and timestamps do not matter, so success bodies are
compared by shape and not by value.
"""

from dataclasses import dataclass
from typing import Any, Optional

# Kinds that the allowlist must never hide: the endpoint does not exist in Rust yet.
MISSING_ROUTE = "missing_route"

# Keys of error bodies that change on every request.
VOLATILE_ERROR_KEYS = frozenset({"timestamp", "traceId", "trace_id", "requestId"})

ROUTE_MISSING_STATUSES = (404, 405)


@dataclass(frozen=True)
class Response:
    status: int
    body: Any = None
    content_type: str = ""


@dataclass(frozen=True)
class Diff:
    path: str
    kind: str
    detail: str


def json_type(value: Any) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    return "object"


def _merge_elements(items: list) -> Any:
    """Collapse array elements into one value that carries the union of their shapes."""
    merged: Any = None
    for item in items:
        merged = item if merged is None else _merge(merged, item)
    return merged


def _merge(a: Any, b: Any) -> Any:
    if a is None:
        return b
    if b is None or json_type(a) != json_type(b):
        return a
    if isinstance(a, dict):
        out = dict(a)
        for key, value in b.items():
            out[key] = _merge(out[key], value) if key in out else value
        return out
    if isinstance(a, list):
        return a + b
    return a


def shape_diffs(java: Any, rust: Any, path: str = "$") -> "list[Diff]":
    jt, rt = json_type(java), json_type(rust)
    if jt != rt:
        return [Diff(path, "type_mismatch", f"java: {jt}, rust: {rt}")]
    if isinstance(java, dict):
        diffs = []
        for key in sorted(java.keys() | rust.keys()):
            child = f"{path}.{key}"
            if key not in rust:
                diffs.append(Diff(child, "missing_in_rust", f"java: {json_type(java[key])}"))
            elif key not in java:
                diffs.append(Diff(child, "extra_in_rust", f"rust: {json_type(rust[key])}"))
            else:
                diffs.extend(shape_diffs(java[key], rust[key], child))
        return diffs
    if isinstance(java, list):
        diffs = []
        if len(java) != len(rust):
            diffs.append(Diff(path, "length_mismatch", f"java: {len(java)}, rust: {len(rust)}"))
        if java and rust:
            diffs.extend(shape_diffs(_merge_elements(java), _merge_elements(rust), f"{path}[]"))
        return diffs
    return []


def _value_diffs(java: Any, rust: Any, path: str = "$") -> "list[Diff]":
    """Error bodies: besides the shape, scalar values must be equal."""
    if isinstance(java, dict) and isinstance(rust, dict):
        diffs = []
        for key in sorted(java.keys() & rust.keys()):
            if key not in VOLATILE_ERROR_KEYS:
                diffs.extend(_value_diffs(java[key], rust[key], f"{path}.{key}"))
        return diffs
    if isinstance(java, list) and isinstance(rust, list):
        diffs = []
        for index, (j, r) in enumerate(zip(java, rust)):
            diffs.extend(_value_diffs(j, r, f"{path}[{index}]"))
        return diffs
    if json_type(java) == json_type(rust) and java != rust:
        return [Diff(path, "value_mismatch", f"java: {java!r}, rust: {rust!r}")]
    return []


def _media_type(content_type: str) -> str:
    return content_type.split(";")[0].strip().lower()


def _strip_volatile(diffs: "list[Diff]") -> "list[Diff]":
    return [d for d in diffs if d.path.rsplit(".", 1)[-1] not in VOLATILE_ERROR_KEYS]


def compare_responses(java: Response, rust: Response) -> "list[Diff]":
    if java.status != rust.status:
        kind = MISSING_ROUTE if rust.status in ROUTE_MISSING_STATUSES else "status_mismatch"
        return [Diff("$", kind, f"java: {java.status}, rust: {rust.status}")]

    if (java.body is None) != (rust.body is None):
        return [Diff("$", "body_presence_mismatch", _presence(java, rust))]
    if java.body is None:
        return []

    java_media, rust_media = _media_type(java.content_type), _media_type(rust.content_type)
    if java_media != rust_media:
        return [Diff("$", "content_type_mismatch", f"java: {java_media}, rust: {rust_media}")]

    is_error = java.status >= 400
    if isinstance(java.body, str) and isinstance(rust.body, str):
        # Plain text (public key, health probe): only error texts have to be identical.
        if is_error and java.body != rust.body:
            return [Diff("$", "value_mismatch", f"java: {java.body!r}, rust: {rust.body!r}")]
        return []

    diffs = _strip_volatile(shape_diffs(java.body, rust.body))
    if is_error:
        diffs.extend(_value_diffs(java.body, rust.body))
    return diffs


def _presence(java: Response, rust: Response) -> str:
    return f"java: {'empty' if java.body is None else 'body'}, rust: {'empty' if rust.body is None else 'body'}"
