"""Command line entry: `python3 -m parity` (run from `contract-tests/`)."""

import argparse
import json
import os
import sys
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Optional

from .allowlist import Allowlist, AllowlistError
from .endpoints import java_endpoints, rust_routes
from .runner import (
    ROLE_ADMIN,
    ROLE_USER,
    ROLE_USER2,
    HttpTarget,
    apply_allowlist,
    covered_endpoints,
    run_scenario,
)
from .scenario import MISSING_ID, SCENARIO

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ALLOWLIST = Path(__file__).resolve().parents[1] / "allowlist.json"
DEFAULT_JAVA_SRC = ROOT / "backend" / "src" / "main" / "java"
DEFAULT_RUST_SRC = ROOT / "rust-backend" / "src"

EXIT_OK, EXIT_MISMATCH, EXIT_ERROR = 0, 1, 2


class HarnessError(RuntimeError):
    pass


def _parser() -> argparse.ArgumentParser:
    env = os.environ.get
    p = argparse.ArgumentParser(prog="parity", description="Contract parity of the Java and Rust backends")
    p.add_argument("--java-url", default=env("CONTRACT_JAVA_URL", "http://localhost:8080"))
    p.add_argument("--java-management-url", default=env("CONTRACT_JAVA_MANAGEMENT_URL"),
                   help="management interface of the Java backend (health, metrics), if it is on another port")
    p.add_argument("--rust-url", default=env("CONTRACT_RUST_URL", "http://localhost:8081"))
    p.add_argument("--allowlist", default=str(DEFAULT_ALLOWLIST))
    p.add_argument("--coverage", action="store_true", help="only check the request definitions against the sources")
    p.add_argument("--java-src", default=str(DEFAULT_JAVA_SRC))
    p.add_argument("--rust-src", default=str(DEFAULT_RUST_SRC))
    p.add_argument("--other-user", default=env("CONTRACT_OTHER_USER_ID", "oidc-user-2"),
                   help="oidc id of a second seeded user (env CONTRACT_OTHER_USER_ID)")
    p.add_argument("--redirect-uri", default=env("CONTRACT_REDIRECT_URI", "https://chronos.example.org/"))
    return p


def fetch_token(token_url: str, client_id: str, username: str, password: str, client_secret: Optional[str]) -> str:
    form = {"grant_type": "password", "client_id": client_id, "username": username, "password": password}
    if client_secret:
        form["client_secret"] = client_secret
    request = urllib.request.Request(token_url, data=urllib.parse.urlencode(form).encode())
    with urllib.request.urlopen(request, timeout=30) as raw:
        return json.load(raw)["access_token"]


def tokens_from_env() -> "dict[str, str]":
    """Same token on both sides: both backends validate against the same Keycloak."""
    env = os.environ.get
    tokens = {}
    for role, token_var, user_var, pass_var in (
        (ROLE_USER, "CONTRACT_USER_TOKEN", "CONTRACT_USER", "CONTRACT_PASSWORD"),
        (ROLE_USER2, "CONTRACT_USER2_TOKEN", "CONTRACT_USER2", "CONTRACT_USER2_PASSWORD"),
        (ROLE_ADMIN, "CONTRACT_ADMIN_TOKEN", "CONTRACT_ADMIN_USER", "CONTRACT_ADMIN_PASSWORD"),
    ):
        if env(token_var):
            tokens[role] = env(token_var)
        elif env("CONTRACT_TOKEN_URL") and env(user_var):
            tokens[role] = fetch_token(
                env("CONTRACT_TOKEN_URL"), env("CONTRACT_CLIENT_ID", ""), env(user_var),
                env(pass_var, ""), env("CONTRACT_CLIENT_SECRET"),
            )
    if ROLE_USER not in tokens:
        raise HarnessError("no user token: set CONTRACT_USER_TOKEN or CONTRACT_TOKEN_URL + CONTRACT_USER")
    # without an admin token the admin steps run as the plain user (403 on both sides)
    tokens.setdefault(ROLE_ADMIN, tokens[ROLE_USER])
    # without a second user the friendship steps of user 2 run as user 1 (same answer on both sides)
    tokens.setdefault(ROLE_USER2, tokens[ROLE_USER])
    return tokens


def coverage_report(steps, java_src: Path, rust_src: Path, allowlist: Allowlist) -> int:
    java = java_endpoints(java_src)
    rust = rust_routes(rust_src)
    uncovered = sorted(java - covered_endpoints(steps))
    rust_only = sorted(r for r in rust - java if f"{r[0]} {r[1]}" not in allowlist.rust_only)
    print("Java endpoints without request definition:")
    for method, path in uncovered:
        print(f"  {method} {path}")
    print("Rust routes without Java counterpart:")
    for method, path in rust_only:
        print(f"  {method} {path}")
    print(f"{len(java)} Java endpoints, {len(java) - len(uncovered)} covered; {len(rust)} Rust routes")
    return EXIT_MISMATCH if uncovered or rust_only else EXIT_OK


def print_report(results, allowlist: Allowlist) -> int:
    mismatches = 0
    allowed = 0
    unchecked = 0
    for result in results:
        step = result.step
        for note in result.unchecked:
            unchecked += 1
            print(f"UNCHECKED {step.name} ({step.method} {step.path}) {note}")
        if result.diffs:
            mismatches += 1
            print(f"MISMATCH {step.name} ({step.method} {step.path})")
            print(f"  status: java {result.java.status}, rust {result.rust.status}")
            for diff in result.diffs:
                print(f"  {diff.path}  {diff.kind}  {diff.detail}")
        for diff in result.allowed:
            allowed += 1
    for entry in allowlist.entries:
        if entry.used:
            print(f"ALLOWED {entry.step} {entry.path} {entry.kind}: {entry.reason}")
    for entry in allowlist.unused():
        print(f"NOTE unused allowlist entry: {entry.step} {entry.path} {entry.kind} ({entry.reason})")
    print(f"{len(results)} steps, {mismatches} mismatches, {allowed} allowed differences, "
          f"{unchecked} arrays with unchecked element shape")
    return EXIT_MISMATCH if mismatches else EXIT_OK


def main(argv=None, targets=None, steps=None) -> int:
    args = _parser().parse_args(argv)
    steps = SCENARIO if steps is None else steps
    try:
        allowlist = Allowlist.load(Path(args.allowlist))
    except (AllowlistError, ValueError) as error:
        print(f"invalid allowlist {args.allowlist}: {error}")
        return EXIT_ERROR
    if args.coverage:
        return coverage_report(steps, Path(args.java_src), Path(args.rust_src), allowlist)
    try:
        if targets is None:
            tokens = tokens_from_env()
            targets = (HttpTarget(args.java_url, tokens, management_url=args.java_management_url),
                       HttpTarget(args.rust_url, tokens))
        results = run_scenario(
            steps, *targets, {
                "other": args.other_user,
                "redirect": args.redirect_uri,
                "provider": "google",
                "unknown_provider": "does-not-exist",
                "missing_user": "does-not-exist",
                "missing_id": MISSING_ID,
            }
        )
    except (ConnectionError, HarnessError, OSError) as error:
        print(f"harness error: {error}")
        return EXIT_ERROR
    apply_allowlist(results, allowlist)
    return print_report(results, allowlist)


if __name__ == "__main__":
    sys.exit(main())
