#!/usr/bin/env python3
"""Performance comparison of the Java and the Rust backend in the local k3s cluster (namespace chronos-parity).

One backend runs at a time (the other is scaled to 0) on a fresh database with the same seed. Requests go straight to the
pod IP (no kubectl port-forward on the data path). Load comes from `oha` (pinned to other cores), CPU and memory are read from
the cgroup of the backend container and of Postgres once per second.

Both backends upsert the identity of the caller into `user_profiles` on every authenticated request. With one user that is one
hot row and every request waits for the previous commit (about 35 requests/s whatever the backend), so the load phases use
PERF_USERS different users with their own data, one `oha` process per user. The single-user case is measured on its own
(`hot_row_single_user`).

    python3 bench.py --out results/round1 --order java,rust
    python3 bench.py --out /tmp/smoke --order rust --quick          # short smoke run

See README.md for the method, the limits that are applied and how to read the output.
"""
import argparse
import http.client
import json
import os
import statistics
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

NS = os.environ.get("PARITY_NAMESPACE", "chronos-parity")
OHA = os.environ.get("OHA", "oha")
OHA_CPUS = os.environ.get("OHA_CPUS", "10-13")  # keep the load generator off the cores the pods mostly use
KC_PORT = int(os.environ.get("PARITY_KC_PORT", "18082"))
LIMITS = {"cpu": "2", "memory": "2Gi"}  # the same for both backends; 768Mi (the chart value) OOM-kills the Java backend, see README
PG_LIMITS = {"cpu": "6", "memory": "2Gi"}  # 2 cores were saturated by the mixed workload (the DB would be the cap, not the backend)
APP = {"java": "chronos-backend", "rust": "chronos-rust-backend"}
DB = {"java": "chronos", "rust": "chronos_rust"}

APPOINTMENT = {"name": "Perf Termin", "description": "Leistungsvergleich", "start": "2030-01-01T18:00:00Z",
               "end": "2030-01-01T20:00:00Z", "venue": "Gemeindehaus", "minimal_attendees": 2}
USERS = int(os.environ.get("PERF_USERS", "16"))
PUSH = {"endpoint": "https://push.example.org/perf", "keys": {"p256dh": "perf-p256dh", "auth": "perf-auth"}}


def sh(*args, check=True, timeout=300):
    r = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if check and r.returncode:
        raise RuntimeError(f"{' '.join(args)}: {r.stderr.strip()[:300]}")
    return r.stdout.strip()


def kubectl(*args, **kw):
    return sh("kubectl", "-n", NS, *args, **kw)


# --- cluster ---------------------------------------------------------------------------------------------------------

def set_limits(restore=None):
    """Equal resource limits for both backends and a Postgres that is not the bottleneck. Returns the original values.

    The originals are kept as an annotation on the deployment, so that a run that was killed (no `finally`) does not make
    the next run take the benchmark values for the originals. The annotation is removed when the originals are restored."""
    names = ["chronos-backend", "chronos-rust-backend", "postgres"]
    old = {}
    kubectl("patch", "deploy/postgres", "-p", '{"spec":{"strategy":{"type":"Recreate","rollingUpdate":null}}}', check=False)
    for n in names:
        current = json.loads(kubectl("get", f"deploy/{n}", "-o", "jsonpath={.spec.template.spec.containers[0].resources}"))
        saved = kubectl("get", f"deploy/{n}", "-o", "jsonpath={.metadata.annotations.perf-original-resources}")
        old[n] = json.loads(saved) if saved else current
        if not saved and not restore:
            kubectl("annotate", f"deploy/{n}", f"perf-original-resources={json.dumps(current)}", "--overwrite")
        want = restore[n] if restore else {"limits": PG_LIMITS if n == "postgres" else LIMITS,
                                            "requests": {"cpu": "500m", "memory": "256Mi"}}
        kubectl("set", "resources", f"deploy/{n}", f"--limits=cpu={want['limits']['cpu']},memory={want['limits']['memory']}",
                f"--requests=cpu={want['requests']['cpu']},memory={want['requests']['memory']}")
        if restore:
            kubectl("annotate", f"deploy/{n}", "perf-original-resources-", check=False)
    return old


def scale(name, n):
    kubectl("scale", f"deploy/{name}", f"--replicas={n}")
    if n == 0:
        kubectl("wait", "--for=delete", "pod", "-l", f"app={name}", "--timeout=300s", check=False)


def reset_db(backend):
    db = DB[backend]
    kubectl("exec", "deploy/postgres", "--", "psql", "-U", "postgres", "-c", f"DROP DATABASE IF EXISTS {db} WITH (FORCE)",
            "-c", f"CREATE DATABASE {db}")


def pod(backend):
    app = APP[backend]
    for _ in range(120):
        out = kubectl("get", "pod", "-l", f"app={app}", "-o", "json")
        items = [i for i in json.loads(out)["items"] if not i["metadata"].get("deletionTimestamp")]
        if len(items) == 1 and items[0]["status"].get("podIP") and items[0]["status"].get("containerStatuses"):
            cid = items[0]["status"]["containerStatuses"][0].get("containerID", "")
            if cid:
                return items[0]["status"]["podIP"], cid.split("//")[-1], items[0]
        time.sleep(1)
    raise RuntimeError(f"no pod for {app}")


def restart_count(backend):
    items = json.loads(kubectl("get", "pod", "-l", f"app={APP[backend]}", "-o", "json"))["items"]
    items = [i for i in items if not i["metadata"].get("deletionTimestamp")]
    if len(items) != 1:
        return None
    st = items[0]["status"]["containerStatuses"][0]
    return {"restarts": st["restartCount"], "container": st["containerID"][-12:]}


def cgroup(container_id):
    out = sh("find", "/sys/fs/cgroup", "-name", f"cri-containerd-{container_id}.scope", check=False)
    return out.splitlines()[0] if out else None


def pg_cgroup():
    cid = kubectl("get", "pod", "-l", "app=postgres", "-o", "jsonpath={.items[0].status.containerStatuses[0].containerID}")
    return cgroup(cid.split("//")[-1])


class Sampler(threading.Thread):
    """Reads cpu and memory from the cgroups once per second."""

    def __init__(self, groups):
        super().__init__(daemon=True)
        self.groups, self.rows, self.stop_flag = groups, [], threading.Event()

    def read(self, path):
        cpu = int([ln for ln in open(f"{path}/cpu.stat") if ln.startswith("usage_usec")][0].split()[1])
        mem = int(open(f"{path}/memory.current").read())
        anon = int([ln for ln in open(f"{path}/memory.stat") if ln.startswith("anon ")][0].split()[1])
        return cpu, mem, anon

    def run(self):
        while not self.stop_flag.is_set():
            row = {"t": time.time()}
            for name, path in self.groups.items():
                try:
                    row[name] = self.read(path)
                except OSError:
                    row[name] = None
            self.rows.append(row)
            self.stop_flag.wait(1.0)

    def window(self, t0, t1):
        """cpu cores (average, peak 1 s) and memory (anon / current, peak and last) per group within [t0, t1]."""
        rows = [r for r in self.rows if t0 <= r["t"] <= t1]
        out = {}
        for name in self.groups:
            pts = [(r["t"], *r[name]) for r in rows if r.get(name)]
            if len(pts) < 2:
                continue
            cores = [(b[1] - a[1]) / 1e6 / (b[0] - a[0]) for a, b in zip(pts, pts[1:]) if b[0] > a[0]]
            out[name] = {"cpu_seconds": round((pts[-1][1] - pts[0][1]) / 1e6, 3),
                         "cpu_cores_avg": round(statistics.mean(cores), 3), "cpu_cores_peak": round(max(cores), 3),
                         "mem_peak_mib": round(max(p[2] for p in pts) / 2**20, 1),
                         "anon_peak_mib": round(max(p[3] for p in pts) / 2**20, 1),
                         "anon_last_mib": round(pts[-1][3] / 2**20, 1)}
        return out


# --- http ------------------------------------------------------------------------------------------------------------

def kc_token(user):
    data = urllib.parse.urlencode({"grant_type": "password", "client_id": "chronos-backend",
                                   "client_secret": "backend-secret", "username": user, "password": user}).encode()
    url = f"http://localhost:{KC_PORT}/realms/chronos/protocol/openid-connect/token"
    return json.load(urllib.request.urlopen(urllib.request.Request(url, data=data), timeout=30))["access_token"]


def kc_admin_token():
    data = urllib.parse.urlencode({"grant_type": "client_credentials", "client_id": "chronos-backend",
                                   "client_secret": "backend-secret"}).encode()
    url = f"http://localhost:{KC_PORT}/realms/chronos/protocol/openid-connect/token"
    return json.load(urllib.request.urlopen(urllib.request.Request(url, data=data), timeout=30))["access_token"]


def ensure_users(n):
    """perf-user-1..n in the realm (password = user name), with the realm role `user` only, like the parity users.
    A user with the default roles gets `aud: account` in its token, which the Rust backend rejects (#174)."""
    token = kc_admin_token()
    base = f"http://localhost:{KC_PORT}/admin/realms/chronos"
    auth = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    def call(method, path, body=None):
        req = urllib.request.Request(base + path, method=method, headers=auth,
                                     data=json.dumps(body).encode() if body is not None else None)
        try:
            raw = urllib.request.urlopen(req, timeout=30).read()
            return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            if e.code != 409:
                raise

    user_role = call("GET", "/roles/user")
    default_role = call("GET", "/roles/default-roles-chronos")
    for i in range(1, n + 1):
        name = f"perf-user-{i}"
        call("POST", "/users", {"username": name, "enabled": True, "emailVerified": True, "firstName": "Perf",
                                "lastName": f"User{i}", "email": f"{name}@parity.local",
                                "credentials": [{"type": "password", "value": name, "temporary": False}]})
        uid = call("GET", f"/users?username={name}&exact=true")[0]["id"]
        call("POST", f"/users/{uid}/role-mappings/realm", [user_role])
        call("DELETE", f"/users/{uid}/role-mappings/realm", [default_role])


class Client:
    def __init__(self, host, port, token):
        self.host, self.port, self.token = host, port, token
        self.conn = http.client.HTTPConnection(host, port, timeout=60)

    def call(self, method, path, body=None):
        headers = {"Authorization": f"Bearer {self.token}"} if self.token else {}
        data = None
        if body is not None:
            data = json.dumps(body).encode() if not isinstance(body, bytes) else body
            headers["Content-Type"] = "application/json"
        for attempt in (0, 1):
            try:
                self.conn.request(method, path, body=data, headers=headers)
                r = self.conn.getresponse()
                raw = r.read()
                return r.status, raw
            except (http.client.HTTPException, OSError):
                self.conn.close()
                self.conn = http.client.HTTPConnection(self.host, self.port, timeout=60)
                if attempt:
                    raise

    def json(self, method, path, body=None):
        status, raw = self.call(method, path, body)
        return status, (json.loads(raw) if raw[:1] in (b"{", b"[") else raw.decode(errors="replace"))


def jvm_metrics(ip, port):
    """Heap and GC of the JVM from the Micrometer endpoint (Java only): the container memory also depends on the heap policy."""
    try:
        c = http.client.HTTPConnection(ip, port, timeout=10)
        c.request("GET", "/metrics")
        text = c.getresponse().read().decode()
    except OSError:
        return None
    out = {"heap_used_mib": 0.0, "heap_committed_mib": 0.0, "nonheap_used_mib": 0.0, "gc_pause_seconds": 0.0, "gc_pauses": 0.0}
    for line in text.splitlines():
        if line.startswith("#"):
            continue
        name, _, value = line.rpartition(" ")
        try:
            v = float(value)
        except ValueError:
            continue
        if name.startswith("jvm_memory_used_bytes") and 'area="heap"' in name:
            out["heap_used_mib"] += v / 2**20
        elif name.startswith("jvm_memory_used_bytes") and 'area="nonheap"' in name:
            out["nonheap_used_mib"] += v / 2**20
        elif name.startswith("jvm_memory_committed_bytes") and 'area="heap"' in name:
            out["heap_committed_mib"] += v / 2**20
        elif name.startswith("jvm_gc_pause_seconds_sum"):
            out["gc_pause_seconds"] += v
        elif name.startswith("jvm_gc_pause_seconds_count"):
            out["gc_pauses"] += v
    return {k: round(v, 2) for k, v in out.items()}


def wait_ready(ip, port, path="/q/health/ready", timeout=300):
    t0 = time.time()
    while time.time() - t0 < timeout:
        try:
            c = http.client.HTTPConnection(ip, port, timeout=2)
            c.request("GET", path)
            if c.getresponse().status == 200:
                return time.time() - t0
        except OSError:
            pass
        time.sleep(0.25)
    raise RuntimeError("backend did not become ready")


# --- seed and endpoints ----------------------------------------------------------------------------------------------

def seed(ip, quick):
    """Every perf user gets own data: 3 appointments (the first is the one that is read), 20 messages, a group and one
    friend (ring: user i befriends user i+1, who becomes a participant of the first appointment)."""
    n = 4 if quick else USERS
    names = [f"perf-user-{i}" for i in range(1, n + 1)]
    tokens = {u: kc_token(u) for u in names}
    api = {u: Client(ip, 8080, tokens[u]) for u in names}
    me = {}
    for u in names:
        s, body = api[u].json("GET", "/api/v2/user")
        assert s == 200, f"GET /user as {u} -> {s} {body}"
        me[u] = body["id"]
    users = []
    for i, u in enumerate(names):
        nxt = names[(i + 1) % n]
        api[u].call("POST", f"/api/v2/friendships/requests?adresse_id={me[nxt]}")
    for i, u in enumerate(names):
        prev = names[(i - 1) % n]
        s, inc = api[u].json("GET", "/api/v2/friendships/requests/incoming")
        assert s == 200 and inc, f"no incoming request for {u}: {s} {inc}"
        api[u].call("POST", f"/api/v2/friendships/requests/{inc[0]['requestId']}/accept")
    for i, u in enumerate(names):
        nxt = names[(i + 1) % n]
        appts = []
        for k in range(3):
            s, a = api[u].json("POST", "/api/v2/appointments", {**APPOINTMENT, "name": f"Perf Termin {u} {k}"})
            assert s in (200, 201), f"create appointment -> {s} {a}"
            appts.append(a["id"])
        s, _ = api[u].json("POST", f"/api/v2/appointments/{appts[0]}/participants/{me[nxt]}", {"user_role": "ATTENDANT"})
        assert s == 200, f"add participant -> {s}"
        for k in range(20):
            api[u].call("POST", f"/api/v2/appointments/{appts[0]}/messages", {"body": f"Nachricht {k}"})
        s, g = api[u].json("POST", "/api/v2/groups", {"name": f"Perf Gruppe {u}"})
        _, settings = api[u].json("GET", "/api/v2/settings")
        users.append({"name": u, "token": tokens[u], "me": me[u], "appt": appts[0], "group": g["id"],
                      "other": me[nxt], "other_token": tokens[nxt], "settings": settings})
    return users


def endpoints(ctx):
    """(name, method, path, body, role, load, port): load = also measured with oha. Reads first, writes last.
    Admin endpoints are latency-only: one admin user would be the single hot row again."""
    a, g = ctx["appt"], ctx["group"]
    emb = "participants=true&messages=true&group_participants=true"
    return [
        ("health ready", "GET", "/q/health/ready", None, "anon", True, "mgmt"),
        ("metrics", "GET", "/metrics", None, "anon", True, "mgmt"),
        ("get user", "GET", "/api/v2/user", None, "user", True, "api"),
        ("get settings", "GET", "/api/v2/settings", None, "user", True, "api"),
        ("list appointments", "GET", "/api/v2/appointments?page=0&size=10", None, "user", True, "api"),
        ("list appointments (embedded)", "GET", f"/api/v2/appointments?page=0&size=10&{emb}", None, "user", True, "api"),
        ("get appointment", "GET", f"/api/v2/appointments/{a}", None, "user", True, "api"),
        ("get appointment (embedded)", "GET", f"/api/v2/appointments/{a}?{emb}", None, "user", True, "api"),
        ("list participants", "GET", f"/api/v2/appointments/{a}/participants", None, "user", True, "api"),
        ("participation status", "GET", f"/api/v2/appointments/{a}/participation/status", None, "user", True, "api"),
        ("list messages", "GET", f"/api/v2/appointments/{a}/messages", None, "user", True, "api"),
        ("list groups", "GET", "/api/v2/groups", None, "user", True, "api"),
        ("list group users", "GET", f"/api/v2/groups/{g}/users", None, "user", True, "api"),
        ("list friends", "GET", "/api/v2/friendships/friends", None, "user", True, "api"),
        ("incoming requests", "GET", "/api/v2/friendships/requests/incoming", None, "user", True, "api"),
        ("push public key", "GET", "/api/v2/push/public-key", None, "user", True, "api"),
        ("admin stats", "GET", "/api/v2/admin/stats", None, "admin", False, "api"),
        ("admin list appointments", "GET", "/api/v2/admin/appointments?page=0&size=5", None, "admin", False, "api"),
        ("friend suggestions (Keycloak)", "GET", "/api/v2/friendships/suggestions", None, "user", False, "api"),
        ("admin list users (Keycloak)", "GET", "/api/v2/admin/users?page=0&size=5", None, "admin", False, "api"),
        ("put settings", "PUT", "/api/v2/settings", ctx["settings"], "user", True, "api"),
        ("create appointment", "POST", "/api/v2/appointments", APPOINTMENT, "user", True, "api"),
        ("patch appointment", "PATCH", f"/api/v2/appointments/{a}", {**APPOINTMENT, "venue": "Kirche"}, "user", True, "api"),
        ("send message", "POST", f"/api/v2/appointments/{a}/messages", {"body": "Hallo zusammen"}, "user", True, "api"),
        ("create group", "POST", "/api/v2/groups", {"name": "Perf Gruppe 2"}, "user", True, "api"),
        ("patch group", "PATCH", f"/api/v2/groups/{g}", {"name": "Perf Gruppe 3"}, "user", True, "api"),
    ]


def stateful(ctx):
    """Latency-only steps that change state back and forth (not repeatable with a fixed body under load).
    (name, method, path, body key or None, values that are used in turn, role)."""
    a, o = ctx["appt"], ctx["other"]
    return [
        ("rsvp (alternating)", "POST", f"/api/v2/appointments/{a}/participation", "status", ["APPROVED", "REJECTED"], "user2"),
        ("change role (alternating)", "PUT", f"/api/v2/appointments/{a}/participation/{o}/role", "role", ["HELPER", "ATTENDANT"], "user"),
        ("push subscribe", "POST", "/api/v2/push/subscribe", None, [PUSH], "user"),
        ("push unsubscribe", "DELETE", f"/api/v2/push/unsubscribe?endpoint={PUSH['endpoint']}", None, [None], "user"),
    ]


def pct(values, p):
    values = sorted(values)
    return values[min(len(values) - 1, int(round(p / 100 * (len(values) - 1))))]


def stats(times, codes):
    return {"p50": round(pct(times, 50), 2), "p95": round(pct(times, 95), 2), "p99": round(pct(times, 99), 2),
            "mean": round(statistics.mean(times), 2), "codes": codes}


def latency_unloaded(hosts, admin_token, users, n, warm):
    """Sequential requests on one keep-alive connection as the first perf user; the same Python client for both backends."""
    ctx = users[0]
    tokens = {"user": ctx["token"], "user2": ctx["other_token"], "admin": admin_token, "anon": None}
    clients = {}

    def client(role, port):
        key = (role, port)
        if key not in clients:
            clients[key] = Client(hosts["ip"], hosts[port], tokens.get(role))
        return clients[key]

    out = {}
    for name, method, path, body, role, _load, port in endpoints(ctx):
        c = client(role, port)
        for _ in range(warm):
            c.call(method, path, body)
        times, codes = [], {}
        for _ in range(n):
            t = time.perf_counter()
            s, _ = c.call(method, path, body)
            times.append((time.perf_counter() - t) * 1000)
            codes[str(s)] = codes.get(str(s), 0) + 1
        out[name] = stats(times, codes)
    for name, method, path, key, values, role in stateful(ctx):
        c = client(role, "api")
        times, codes = [], {}
        for i in range(n + warm):
            value = values[i % len(values)]
            body = {key: value} if key else value
            t = time.perf_counter()
            s, _ = c.call(method, path, body)
            dt = (time.perf_counter() - t) * 1000
            if i >= warm:
                times.append(dt)
                codes[str(s)] = codes.get(str(s), 0) + 1
        out[name] = stats(times, codes)
    return out


# --- load ------------------------------------------------------------------------------------------------------------

def oha(url, method, body, token, conc, seconds, outfile):
    cmd = ["taskset", "-c", OHA_CPUS, OHA, "--no-tui", "--output-format", "json", "-z", f"{seconds}s", "-c", str(conc), "-w",
           "--worker-threads", "1", "-m", method, "-H", f"Authorization: Bearer {token}" if token else "X-Perf: 1"]
    if body is not None:
        cmd += ["-T", "application/json", "-d", json.dumps(body)]
    cmd.append(url)
    return subprocess.Popen(cmd, stdout=open(outfile, "w"), stderr=subprocess.DEVNULL)


def parse_oha(path):
    d = json.load(open(path))
    s = d["summary"]
    codes = dict(d.get("statusCodeDistribution", {}))
    total = sum(codes.values())
    ok = sum(v for k, v in codes.items() if k.startswith("2"))
    lp = d.get("latencyPercentiles", {})
    ms = lambda v: (v or 0) * 1000  # oha prints null percentiles when no request completed
    return {"rps": s["requestsPerSec"], "requests": total, "ok": ok,
            "errors": total - ok + sum(d.get("errorDistribution", {}).values()),
            "p50_ms": ms(lp.get("p50")), "p95_ms": ms(lp.get("p95")), "p99_ms": ms(lp.get("p99")),
            "mean_ms": ms(s.get("average")), "codes": codes}


def aggregate(parts):
    """Several oha processes (one per user) as one: throughput and counts add up, p50/p95/mean are weighted by the number
    of requests, p99 is the worst of the processes (a merged percentile needs the raw samples)."""
    total = sum(p["requests"] for p in parts) or 1
    codes = {}
    for p in parts:
        for k, v in p["codes"].items():
            codes[k] = codes.get(k, 0) + v
    w = lambda key: sum(p[key] * p["requests"] for p in parts) / total
    return {"rps": round(sum(p["rps"] for p in parts), 1), "requests": total if parts else 0,
            "ok": sum(p["ok"] for p in parts), "errors": sum(p["errors"] for p in parts),
            "p50_ms": round(w("p50_ms"), 2), "p95_ms": round(w("p95_ms"), 2), "p99_ms": round(max(p["p99_ms"] for p in parts), 2),
            "mean_ms": round(w("mean_ms"), 2), "codes": codes, "processes": len(parts)}


def base_url(hosts, port):
    return f"http://{hosts['ip']}:{hosts[port]}"


def spawn(hosts, users, ep, total_c, seconds, tmp, tag):
    """oha processes for one endpoint, total_c connections spread over the users (one process per user, anon: one process)."""
    name, method, path, body, role, _load, port = ep
    procs = []
    if role == "anon":
        procs.append(oha(base_url(hosts, port) + path, method, body, None, total_c, seconds, f"{tmp}/{tag}-0.json"))
    else:
        k = min(len(users), total_c)
        per = max(1, round(total_c / k))
        for i, u in enumerate(users[:k]):
            _n, m, p, b, r, _l, prt = [e for e in endpoints(u) if e[0] == name][0]
            procs.append(oha(base_url(hosts, prt) + p, m, b, u["token"], per, seconds, f"{tmp}/{tag}-{i}.json"))
    return procs


def calibrate(hosts, tmp, quick):
    """Plain liveness probe (no authentication, no database): the ceiling of pod network, VM and load generator."""
    out = {}
    for c in ([1, 16] if quick else [1, 16, 64]):
        f = f"{tmp}/cal-{c}.json"
        oha(base_url(hosts, "mgmt") + "/q/health/live", "GET", None, None, c, 3 if quick else 8, f).wait()
        r = parse_oha(f)
        out[str(c)] = {"rps": round(r["rps"], 1), "p50_ms": round(r["p50_ms"], 2), "p99_ms": round(r["p99_ms"], 2),
                       "errors": r["errors"]}
        time.sleep(1)
    return out


def hot_row(hosts, users, tmp, quick):
    """One user, several connections: every request upserts the same user_profiles row (both backends)."""
    out = {}
    ep = [e for e in endpoints(users[0]) if e[0] == "push public key"][0]
    for c in ([1, 8] if quick else [1, 4, 16]):
        procs = spawn(hosts, users[:1], ep, c, 3 if quick else 8, tmp, f"hot-{c}")
        for p in procs:
            p.wait()
        out[str(c)] = {k: round(v, 1) if isinstance(v, float) else v for k, v in aggregate(
            [parse_oha(f"{tmp}/hot-{c}-0.json")]).items() if k in ("rps", "p50_ms", "p99_ms", "errors")}
        time.sleep(1)
    return out


MIX = [  # name in endpoints() -> weight in percent of the concurrency
    ("list appointments", 25), ("get appointment", 20), ("list messages", 10), ("get user", 5), ("get settings", 5),
    ("list groups", 5), ("list friends", 5), ("get appointment (embedded)", 5), ("send message", 10),
    ("create appointment", 5), ("patch appointment", 5)]


def run_load(hosts, users, sampler, tmp, quick):
    seconds = 4 if quick else 15
    per_endpoint = {}
    for ep in endpoints(users[0]):
        if not ep[5]:
            continue
        t0 = time.time()
        procs = spawn(hosts, users, ep, 32, seconds, tmp, f"ep{len(per_endpoint)}")
        for p in procs:
            p.wait()
        t1 = time.time()
        parts = [parse_oha(f"{tmp}/ep{len(per_endpoint)}-{i}.json") for i in range(len(procs))]
        res = aggregate(parts)
        res["resources"] = sampler.window(t0 + 1, t1)
        res["cpu_ms_per_request"] = round(res["resources"].get("backend", {}).get("cpu_seconds", 0) * 1000 / max(res["requests"], 1), 3)
        per_endpoint[ep[0]] = res
        time.sleep(2)
    mixed = {}
    eps = {e[0]: e for e in endpoints(users[0])}
    for total_c in ([8, 32] if quick else [16, 64, 256]):
        procs, files = [], []
        t0 = time.time()
        for name, weight in MIX:
            conc = max(1, round(total_c * weight / 100))
            for i, p in enumerate(spawn(hosts, users, eps[name], conc, seconds * (1 if quick else 3), tmp, f"mix{total_c}-{len(files)}")):
                procs.append(p)
            files.append((name, len(procs)))
        for p in procs:
            p.wait()
        t1 = time.time()
        parts, idx, start = {}, 0, 0
        for name, end in files:
            names = [f"{tmp}/mix{total_c}-{files.index((name, end))}-{i}.json" for i in range(end - start)]
            parts[name] = aggregate([parse_oha(f) for f in names])
            start = end
        reqs = sum(v["requests"] for v in parts.values())
        window = sampler.window(t0 + 2, t1)
        mixed[str(total_c)] = {"processes": len(procs), "requests": reqs, "rps": round(sum(v["rps"] for v in parts.values()), 1),
                               "errors": sum(v["errors"] for v in parts.values()),
                               "p95_ms_worst_endpoint": max(v["p95_ms"] for v in parts.values()),
                               "per_endpoint": parts, "resources": window,
                               "cpu_ms_per_request": round(window.get("backend", {}).get("cpu_seconds", 0) * 1000 / max(reqs, 1), 3)}
        time.sleep(5)
    return per_endpoint, mixed


# --- one backend -----------------------------------------------------------------------------------------------------

def run_backend(backend, out_dir, quick):
    other = "rust" if backend == "java" else "java"
    scale(APP[other], 0)
    scale(APP[backend], 0)
    reset_db(backend)
    t_scale = time.time()
    kubectl("scale", f"deploy/{APP[backend]}", "--replicas=1")
    ip, cid, _ = pod(backend)
    mgmt = 9000 if backend == "java" else 8080
    wait_ready(ip, mgmt)
    hosts = {"ip": ip, "api": 8080, "mgmt": mgmt}
    startup = round(time.time() - t_scale, 1)
    started = restart_count(backend)
    sampler = Sampler({"backend": cgroup(cid), "postgres": pg_cgroup()})
    sampler.start()
    time.sleep(2 if quick else 20)
    idle = sampler.window(time.time() - (2 if quick else 15), time.time())
    jvm_idle = jvm_metrics(ip, mgmt) if backend == "java" else None
    admin_token = kc_token("parity-admin")
    users = seed(ip, quick)
    tmp = f"{out_dir}/raw-{backend}"
    os.makedirs(tmp, exist_ok=True)
    # JIT and connection warm-up with the mixed workload over all users, not measured
    warm = []
    eps = {e[0]: e for e in endpoints(users[0])}
    for name, weight in MIX:
        warm += spawn(hosts, users, eps[name], max(1, round(16 * weight / 100)), 5 if quick else 30, tmp, f"warm-{len(warm)}")
    for p in warm:
        p.wait()
    time.sleep(3)
    t0 = time.time()
    calibration = calibrate(hosts, tmp, quick)
    unloaded = latency_unloaded(hosts, admin_token, users, 40 if quick else 300, 10 if quick else 50)
    per_endpoint, mixed = run_load(hosts, users, sampler, tmp, quick)
    hot = hot_row(hosts, users, tmp, quick)
    time.sleep(2 if quick else 30)
    after = sampler.window(time.time() - (2 if quick else 20), time.time())
    jvm_after = jvm_metrics(ip, mgmt) if backend == "java" else None
    sampler.stop_flag.set()
    ended = restart_count(backend)
    _, _, podinfo = pod(backend)
    result = {"backend": backend, "users": len(users), "startup_seconds_to_ready": startup, "limits": LIMITS,
              "container_before": started, "container_after": ended, "restarted": started != ended,
              "idle": idle, "after_load_idle": after, "jvm_idle": jvm_idle, "jvm_after_load": jvm_after, "calibration_live_probe": calibration, "unloaded_ms": unloaded,
              "per_endpoint_c32": per_endpoint, "mixed": mixed, "hot_row_single_user": hot,
              "image": podinfo["status"]["containerStatuses"][0].get("image"),
              "image_id": podinfo["status"]["containerStatuses"][0].get("imageID"),
              "started": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(t0))}
    json.dump(result, open(f"{out_dir}/{backend}.json", "w"), indent=1)
    json.dump(sampler.rows, open(f"{out_dir}/{backend}-samples.json", "w"))
    if result["restarted"]:
        print(f"WARNING: the {backend} container was restarted during the run, results are invalid", flush=True)
    return result


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--order", default="java,rust")
    ap.add_argument("--quick", action="store_true")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    kc = subprocess.Popen(["kubectl", "-n", NS, "port-forward", "svc/keycloak", f"{KC_PORT}:8080"],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(3)
    old = set_limits()
    json.dump(old, open(f"{args.out}/original-limits.json", "w"), indent=1)
    try:
        ensure_users(4 if args.quick else USERS)
        for backend in args.order.split(","):
            print(time.strftime("%T"), "backend", backend, flush=True)
            run_backend(backend, args.out, args.quick)
            print(time.strftime("%T"), "done", backend, flush=True)
    finally:
        kc.terminate()
        set_limits(restore=old)
        for b in ("java", "rust"):  # leave the namespace as the parity run expects it
            kubectl("scale", f"deploy/{APP[b]}", "--replicas=1", check=False)


if __name__ == "__main__":
    main()
