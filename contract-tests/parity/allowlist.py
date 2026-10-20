"""Deliberate differences between Java and Rust, each with a mandatory reason."""

import json
from dataclasses import dataclass, field
from fnmatch import fnmatchcase
from pathlib import Path
from typing import Optional

from .compare import MISSING_ROUTE, Diff

_KEYS = {"step", "kind", "path", "reason"}


class AllowlistError(ValueError):
    pass


@dataclass
class Entry:
    step: str
    kind: str
    path: str
    reason: str
    used: bool = field(default=False, compare=False)


class Allowlist:
    def __init__(self, entries: "list[Entry]", rust_only: "Optional[dict[str, str]]" = None):
        self.entries = entries
        # Rust routes without a Java counterpart that are deliberate: "METHOD /path" -> reason
        self.rust_only = rust_only or {}

    @staticmethod
    def _rust_only(raw: "list[dict]") -> "dict[str, str]":
        routes = {}
        for index, item in enumerate(raw):
            reason = item.get("reason")
            if not item.get("route") or not isinstance(reason, str) or not reason.strip():
                raise AllowlistError(f"rust_only {index}: route and a reason are required")
            routes[item["route"]] = reason.strip()
        return routes

    @classmethod
    def from_entries(cls, raw: "list[dict]") -> "Allowlist":
        entries = []
        for index, item in enumerate(raw):
            unknown = set(item) - _KEYS
            if unknown:
                raise AllowlistError(f"entry {index}: unknown keys {sorted(unknown)}")
            missing = [k for k in ("step", "kind", "path") if not item.get(k)]
            if missing:
                raise AllowlistError(f"entry {index}: missing {missing}")
            reason = item.get("reason")
            if not isinstance(reason, str) or not reason.strip():
                raise AllowlistError(f"entry {index} ({item['step']}): a reason is required")
            entries.append(Entry(item["step"], item["kind"], item["path"], reason.strip()))
        return cls(entries)

    @classmethod
    def load(cls, path: Optional[Path]) -> "Allowlist":
        if path is None:
            return cls([])
        if not Path(path).is_file():
            raise AllowlistError(f"allowlist file not found: {path}")
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        allowlist = cls.from_entries(data.get("entries", []))
        allowlist.rust_only = cls._rust_only(data.get("rust_only", []))
        return allowlist

    def allows(self, step: str, diff: Diff) -> bool:
        # A route that Rust does not serve yet must stay visible until its ticket is done.
        if diff.kind == MISSING_ROUTE:
            return False
        for entry in self.entries:
            if (
                entry.kind == diff.kind
                and fnmatchcase(step, entry.step)
                and fnmatchcase(diff.path, entry.path)
            ):
                entry.used = True
                return True
        return False

    def unused(self) -> "list[Entry]":
        return [e for e in self.entries if not e.used]
