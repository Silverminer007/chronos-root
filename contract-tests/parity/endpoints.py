"""Endpoint inventory taken from the sources, for the `--coverage` mode.

Java (`@Path`/`@GET`/... in `backend/`) is the reference. The Rust routes are read
from `.route(...)` calls in `rust-backend/src`. Both are normalised to
`(METHOD, "/a/{}/b")` so path parameter names do not matter.
"""

import re
from pathlib import Path

HTTP_VERBS = ("GET", "POST", "PUT", "PATCH", "DELETE")

# Provided by Quarkus extensions, so there is no JAX-RS resource for them.
JAVA_BUILTIN_ENDPOINTS = {
    ("GET", "/metrics"),
    ("GET", "/q/health/live"),
    ("GET", "/q/health/ready"),
}

_PATH_RE = re.compile(r'@Path\("([^"]*)"\)')
_VERB_RE = re.compile(r"^\s*@(" + "|".join(HTTP_VERBS) + r")\b")
_ROUTE_RE = re.compile(r'\.route\(\s*"([^"]+)"\s*,\s*', re.S)
_RUST_VERB_RE = re.compile(r"\b(get|post|put|patch|delete)\(")


def normalize(path: str) -> str:
    path = re.sub(r"\{[^}]*\}|:[A-Za-z_][A-Za-z0-9_]*", "{}", path)
    path = re.sub(r"/+", "/", path)
    return path.rstrip("/") or "/"


def java_endpoints_from_source(source: str) -> "set[tuple[str, str]]":
    lines = source.splitlines()
    class_index = next((i for i, l in enumerate(lines) if re.search(r"\bclass\s+\w+", l)), None)
    if class_index is None:
        return set()
    class_paths = [m.group(1) for l in lines[:class_index] for m in _PATH_RE.finditer(l)]
    # Some resources carry the full path on each method and no class-level @Path.
    base = class_paths[0] if class_paths else ""

    endpoints = set()
    index = class_index + 1
    while index < len(lines):
        match = _VERB_RE.match(lines[index])
        if not match:
            index += 1
            continue
        verb, method_path = match.group(1), ""
        # @Path may also precede the verb annotation
        back = index - 1
        while back > class_index and lines[back].lstrip().startswith("@"):
            path_match = _PATH_RE.search(lines[back])
            if path_match:
                method_path = path_match.group(1)
            back -= 1
        # the annotation block ends at the method signature
        while index < len(lines) and not re.match(r"\s*(public|protected|private)\b", lines[index]):
            path_match = _PATH_RE.search(lines[index])
            if path_match:
                method_path = path_match.group(1)
            index += 1
        endpoints.add((verb, normalize(f"{base}/{method_path}")))
    return endpoints


def java_endpoints(root: Path) -> "set[tuple[str, str]]":
    endpoints = set(JAVA_BUILTIN_ENDPOINTS)
    for file in sorted(root.rglob("*Resource.java")):
        endpoints |= java_endpoints_from_source(file.read_text(encoding="utf-8"))
    return endpoints


def _balanced_call(source: str, start: int) -> str:
    """Text from `start` up to the parenthesis closing the call that opened just before it."""
    depth = 1
    for pos in range(start, len(source)):
        if source[pos] == "(":
            depth += 1
        elif source[pos] == ")":
            depth -= 1
            if depth == 0:
                return source[start:pos]
    return source[start:]


def rust_routes_from_source(source: str) -> "set[tuple[str, str]]":
    routes = set()
    for match in _ROUTE_RE.finditer(source):
        handlers = _balanced_call(source, match.end())
        # drop `.with_state((a, b))` and similar argument lists before reading verbs
        for verb in _RUST_VERB_RE.findall(handlers):
            routes.add((verb.upper(), normalize(match.group(1))))
    return routes


def rust_routes(root: Path) -> "set[tuple[str, str]]":
    routes = set()
    for file in sorted(root.rglob("*.rs")):
        routes |= rust_routes_from_source(file.read_text(encoding="utf-8"))
    return routes
