#!/usr/bin/env python3
"""Turns the results of bench.py (one directory per round, each with java.json and rust.json) into markdown tables.

    python3 report.py results/round1 results/round2 > tables.md

Numbers of several rounds are averaged; a value is marked with "~" when the rounds differ by more than 30 %.
"""
import json
import os
import statistics
import subprocess
import sys


def load(dirs):
    data = {"java": [], "rust": []}
    for d in dirs:
        for b in data:
            p = os.path.join(d, f"{b}.json")
            if os.path.exists(p):
                data[b].append(json.load(open(p)))
    return data


def avg(values):
    values = [v for v in values if v is not None]
    if not values:
        return None, False
    m = statistics.mean(values)
    unstable = len(values) > 1 and m and (max(values) - min(values)) / m > 0.30
    return m, unstable


def fmt(v, unstable=False, digits=1, unit=""):
    if v is None:
        return "-"
    s = f"{v:,.{digits}f}{unit}"
    return ("~" if unstable else "") + s


def get(rounds, *path):
    out = []
    for r in rounds:
        cur = r
        for p in path:
            cur = cur.get(p) if isinstance(cur, dict) else None
            if cur is None:
                break
        out.append(cur)
    return avg(out)


def ratio(a, b, digits=1):
    """a / b as 'x'."""
    if not a or not b:
        return "-"
    return f"{a / b:.{digits}f}x"


def table(rows, header):
    lines = ["| " + " | ".join(header) + " |", "|" + "|".join("---" if i == 0 else "---:" for i in range(len(header))) + "|"]
    lines += ["| " + " | ".join(str(c) for c in r) + " |" for r in rows]
    return "\n".join(lines)


def image_sizes():
    out = {}
    try:
        text = subprocess.run(["sudo", "-n", "k3s", "ctr", "-n", "k8s.io", "images", "ls"], capture_output=True, text=True).stdout
        for line in text.splitlines():
            cols = line.split()
            if len(cols) >= 5 and ("chronos-backend:parity-patched" in cols[0] or "chronos-rust-backend:parity" in cols[0]):
                key = "java" if "chronos-backend" in cols[0] else "rust"
                out[key] = " ".join(cols[3:5])
    except OSError:
        pass
    return out


def main(dirs):
    d = load(dirs)
    j, r = d["java"], d["rust"]
    if not j or not r:
        sys.exit("need java.json and rust.json in at least one round")
    n = min(len(j), len(r))
    print(f"_{n} round(s), averaged; `~` marks a value whose rounds differ by more than 30 %. Rust / Java ratio below 1 is better for Rust "
          f"(latency, CPU, memory), above 1 is better for Rust (throughput)._\n")

    # 1 startup, idle memory
    rows = []
    for label, path, digits in (("Start until `/q/health/ready` answers 200 (s)", ("startup_seconds_to_ready",), 1),
                                ("Memory after start, idle, anon (MiB)", ("idle", "backend", "anon_last_mib"), 1),
                                ("Memory after all load phases, idle, anon (MiB)", ("after_load_idle", "backend", "anon_last_mib"), 1),
                                ("Memory peak under load, anon (MiB, without page cache)", None, 1)):
        if path is None:
            jm = avg([max((v["resources"].get("backend", {}).get("anon_peak_mib", 0) for v in x["mixed"].values()), default=None) for x in j])
            rm = avg([max((v["resources"].get("backend", {}).get("anon_peak_mib", 0) for v in x["mixed"].values()), default=None) for x in r])
        else:
            jm, rm = get(j, *path), get(r, *path)
        rows.append((label, fmt(*jm, digits), fmt(*rm, digits), ratio(rm[0], jm[0])))
    sizes = image_sizes()
    if sizes:
        rows.append(("Container image (containerd)", sizes.get("java", "-"), sizes.get("rust", "-"), ""))
    print("### Start, idle and peak memory\n")
    print(table(rows, ["", "Java", "Rust", "Rust / Java"]))

    # 2 calibration
    print("\n### Measurement floor (plain liveness probe, no authentication, no database)\n")
    rows = []
    for c in ("1", "16", "64"):
        jr, rr = get(j, "calibration_live_probe", c, "rps"), get(r, "calibration_live_probe", c, "rps")
        jp, rp = get(j, "calibration_live_probe", c, "p50_ms"), get(r, "calibration_live_probe", c, "p50_ms")
        rows.append((c, fmt(*jr, 0), fmt(*rr, 0), fmt(*jp, 2), fmt(*rp, 2)))
    print(table(rows, ["Connections", "Java req/s", "Rust req/s", "Java p50 (ms)", "Rust p50 (ms)"]))

    # 3 unloaded latency
    print("\n### Response time per endpoint, one connection, no load (ms)\n")
    names = list(j[0]["unloaded_ms"].keys())
    rows = []
    for name in names:
        jp50, jp95 = get(j, "unloaded_ms", name, "p50"), get(j, "unloaded_ms", name, "p95")
        rp50, rp95 = get(r, "unloaded_ms", name, "p50"), get(r, "unloaded_ms", name, "p95")
        codes = lambda x: "/".join(sorted(x[0]["unloaded_ms"][name]["codes"])) if x else "-"
        status = codes(j) if codes(j) == codes(r) else f"{codes(j)} vs {codes(r)}"
        rows.append((name, status, fmt(*jp50, 2), fmt(*rp50, 2), ratio(rp50[0], jp50[0]), fmt(*jp95, 2), fmt(*rp95, 2)))
    print(table(rows, ["Endpoint", "Status", "Java p50", "Rust p50", "Rust / Java", "Java p95", "Rust p95"]))

    # 4 load per endpoint
    print("\n### Per endpoint under load: 32 connections, 15 s each\n")
    names = list(j[0]["per_endpoint_c32"].keys())
    rows = []
    for name in names:
        g = lambda x, *p: get(x, "per_endpoint_c32", name, *p)
        jr, rr = g(j, "rps"), g(r, "rps")
        rows.append((name, fmt(*jr, 0), fmt(*rr, 0), ratio(rr[0], jr[0]), fmt(*g(j, "p50_ms"), 1), fmt(*g(r, "p50_ms"), 1),
                     fmt(*g(j, "p99_ms"), 1), fmt(*g(r, "p99_ms"), 1), fmt(*g(j, "cpu_ms_per_request"), 2),
                     fmt(*g(r, "cpu_ms_per_request"), 2), f"{int(g(j, 'errors')[0] or 0)} / {int(g(r, 'errors')[0] or 0)}"))
    print(table(rows, ["Endpoint", "Java req/s", "Rust req/s", "Rust / Java", "Java p50", "Rust p50", "Java p99", "Rust p99",
                       "Java CPU ms/req", "Rust CPU ms/req", "Errors J / R"]))

    # 5 mixed workload
    print("\n### Mixed workload, ramp of concurrent connections (60 % reads of 11 endpoints, 20 % writes), 45 s per step\n")
    levels = sorted(j[0]["mixed"].keys(), key=int)
    rows = []
    for lv in levels:
        for label, x in (("Java", j), ("Rust", r)):
            g = lambda *p: get(x, "mixed", lv, *p)
            rows.append((lv, label, fmt(*g("rps"), 0), fmt(*g("p95_ms_worst_endpoint"), 0), int(g("errors")[0] or 0),
                         fmt(*g("resources", "backend", "cpu_cores_avg"), 2), fmt(*g("resources", "backend", "cpu_cores_peak"), 2),
                         fmt(*g("resources", "backend", "anon_peak_mib"), 0), fmt(*g("resources", "postgres", "cpu_cores_avg"), 2),
                         fmt(*g("cpu_ms_per_request"), 2)))
    print(table(rows, ["Connections", "Backend", "Req/s", "p95 worst endpoint (ms)", "Errors", "CPU cores avg", "CPU cores peak",
                       "Memory peak anon (MiB)", "Postgres cores avg", "Backend CPU ms/req"]))


if __name__ == "__main__":
    main(sys.argv[1:])
