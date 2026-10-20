"""Runs a scenario against the Java and the Rust backend and compares each step."""

import json
import re
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Any, Optional

from .compare import Diff, Response, compare_responses, unchecked_shapes
from .endpoints import normalize

ROLE_USER = "user"
ROLE_ADMIN = "admin"
ROLE_USER2 = "user2"
ROLE_ANON = "anon"

# Used for a variable that a failed step could not capture. The request still goes out, so a
# backend that failed to create the resource shows up as a status mismatch and is not skipped.
UNKNOWN_ID = "0"

# Paths served by the management interface of the Java backend when `--java-management-url` is set
MANAGEMENT_PATHS = ("/q/health", "/metrics")

_PLACEHOLDER = re.compile(r"\{(\w+)\}")
_WHOLE_PLACEHOLDER = re.compile(r"^\{(\w+)\}$")


@dataclass(frozen=True)
class Step:
    name: str
    method: str
    path: str
    body: Any = None
    role: str = ROLE_USER
    capture: dict = field(default_factory=dict)  # variable -> dotted path in the response body


@dataclass
class StepResult:
    step: Step
    java: Response
    rust: Response
    diffs: "list[Diff]"
    allowed: "list[Diff]" = field(default_factory=list)
    unchecked: "list[str]" = field(default_factory=list)  # arrays whose element shape was not compared


class Target:
    """One backend; `send` returns its response for a request issued as `role`."""

    def send(self, method: str, path: str, body: Any, role: str) -> Response:
        raise NotImplementedError


class HttpTarget(Target):
    def __init__(self, base_url: str, tokens: "dict[str, str]", timeout: float = 30.0, management_url: Optional[str] = None):
        self.base_url = base_url.rstrip("/")
        self.tokens = tokens
        self.timeout = timeout
        self.management_url = management_url.rstrip("/") if management_url else None

    def _base_for(self, path: str) -> str:
        """Quarkus serves health and metrics on a separate management port (`quarkus.management.enabled`)."""
        if self.management_url and path.startswith(MANAGEMENT_PATHS):
            return self.management_url
        return self.base_url

    def send(self, method: str, path: str, body: Any, role: str) -> Response:
        headers = {"Accept": "application/json, text/plain"}
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = (body if isinstance(body, str) else json.dumps(body)).encode("utf-8")
        token = self.tokens.get(role)
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = urllib.request.Request(self._base_for(path) + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as raw:
                return _to_response(raw.status, raw.headers.get("Content-Type", ""), raw.read())
        except urllib.error.HTTPError as error:
            return _to_response(error.code, error.headers.get("Content-Type", ""), error.read())
        except (urllib.error.URLError, TimeoutError) as error:
            raise ConnectionError(f"{method} {self._base_for(path)}{path}: {error}") from error


def _to_response(status: int, content_type: str, raw: bytes) -> Response:
    text = raw.decode("utf-8", errors="replace")
    if not text.strip():
        return Response(status, None, content_type)
    if "json" in content_type.lower():
        try:
            return Response(status, json.loads(text), content_type)
        except json.JSONDecodeError:
            pass
    return Response(status, text, content_type)


def expand(value: Any, context: dict) -> Any:
    """Replace `{var}` placeholders. A string that is only a placeholder keeps the variable's type."""
    if isinstance(value, str):
        whole = _WHOLE_PLACEHOLDER.match(value)
        if whole:
            return context[whole.group(1)]
        return _PLACEHOLDER.sub(lambda m: str(context[m.group(1)]), value)
    if isinstance(value, list):
        return [expand(v, context) for v in value]
    if isinstance(value, dict):
        return {k: expand(v, context) for k, v in value.items()}
    return value


def extract(body: Any, path: str) -> Any:
    if path == "$":
        return body
    current = body
    for part in path.split("."):
        if isinstance(current, dict):
            current = current.get(part)
        elif isinstance(current, list) and part.isdigit() and int(part) < len(current):
            current = current[int(part)]
        else:
            return None
    return current


def _send(target: Target, step: Step, context: dict) -> Response:
    path = expand(step.path, context)
    body = expand(step.body, context)
    return target.send(step.method, path, body, step.role)


def _capture(step: Step, response: Response, context: dict) -> None:
    for variable, path in step.capture.items():
        value = extract(response.body, path) if response.status < 400 else None
        context[variable] = UNKNOWN_ID if value is None else value


def run_scenario(steps: "list[Step]", java: Target, rust: Target, variables: dict) -> "list[StepResult]":
    java_ctx, rust_ctx = dict(variables), dict(variables)
    results = []
    for step in steps:
        java_resp = _send(java, step, java_ctx)
        rust_resp = _send(rust, step, rust_ctx)
        _capture(step, java_resp, java_ctx)
        _capture(step, rust_resp, rust_ctx)
        diffs = compare_responses(java_resp, rust_resp)
        unchecked = []
        if java_resp.status == rust_resp.status and java_resp.status < 400:
            unchecked = unchecked_shapes(java_resp.body, rust_resp.body)
        results.append(StepResult(step, java_resp, rust_resp, diffs, unchecked=unchecked))
    return results


def covered_endpoints(steps: "list[Step]") -> "set[tuple[str, str]]":
    return {(s.method, normalize(s.path.split("?")[0])) for s in steps}


def apply_allowlist(results: "list[StepResult]", allowlist) -> None:
    for result in results:
        remaining = []
        for diff in result.diffs:
            (result.allowed if allowlist.allows(result.step.name, diff) else remaining).append(diff)
        result.diffs = remaining
