"""Runs one prototype binary on the host (taskset 2 CPUs) against the cluster Postgres and measures four endpoints."""
import json, os, signal, subprocess, sys, time
sys.path.insert(0, "/tmp/perf-diag/tools")
import bench
bench.KC_PORT = 18082
label, binary = sys.argv[1], sys.argv[2]
extra_env = dict(a.split("=", 1) for a in sys.argv[3:])
ENDPOINTS = ["get appointment", "list appointments"]
BULK = int(os.environ.get("BULK", "0")); INDEX = os.environ.get("INDEX", "0") == "1"
env = dict(os.environ)
for l in open("/tmp/perf-diag/env"):
    if "=" in l: k, v = l.rstrip("\n").split("=", 1); env[k] = v
env.update(extra_env)
kc = subprocess.Popen(["kubectl", "-n", bench.NS, "port-forward", "svc/keycloak", "18082:8080"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(3)
old = bench.set_limits()
res, proc = {}, None
try:
    bench.ensure_users(16)
    for b in ("java", "rust"): bench.scale(bench.APP[b], 0)
    bench.kubectl("rollout", "status", "deploy/postgres", "--timeout=200s")
    import re
    pgip = bench.kubectl("get", "pod", "-l", "app=postgres", "--field-selector=status.phase=Running", "-o", "jsonpath={.items[0].status.podIP}")
    env["DATABASE_URL"] = re.sub(r"@[^:/]+:5432", f"@{pgip}:5432", env["DATABASE_URL"])
    bench.reset_db("rust")
    log = open(f"/tmp/perf-diag/proto/{label}.log", "w")
    proc = subprocess.Popen(["taskset", "-c", "4,5", binary], env=env, stdout=log, stderr=subprocess.STDOUT)
    bench.wait_ready("127.0.0.1", 18088)
    bench.pod = lambda backend: ("127.0.0.1", "", None)
    # the seed helper talks to port 8080: patch Client port through a thin wrapper
    orig = bench.Client.__init__
    bench.Client.__init__ = lambda self, host, port, token: orig(self, host, 18088 if port == 8080 else port, token)
    users = bench.seed("127.0.0.1", False)
    def sql(q): return bench.kubectl("exec", "deploy/postgres", "--", "psql", "-U", "postgres", "-d", "chronos_rust", "-c", q)
    if BULK:
        sql(f"INSERT INTO appointment (id, createdat, description, end_time, lastupdate, minimalattendees, name, start_time, status, venue) SELECT 10000000+g, now(), 'bulk', now() + interval '2 days', now(), 1, 'Bulk '||g, now() + interval '1 day', 0, 'Ort' FROM generate_series(1,{BULK}) g")
        sql(f"INSERT INTO appointment_participation (id, group_participation_id, role, status, appointment_id, user_oidcid) SELECT 10000000+g, NULL, 'ATTENDANT', 'PENDING', 10000000+g, 'bulk-user-' || (g % 2000) FROM generate_series(1,{BULK}) g")
    if INDEX:
        sql("CREATE INDEX idx_appointment_participation_user ON appointment_participation (user_oidcid, appointment_id)")
    sql("ANALYZE")
    hosts = {"ip": "127.0.0.1", "api": 18088, "mgmt": 18088}
    eps = {e[0]: e for e in bench.endpoints(users[0])}
    os.makedirs("/tmp/perf-diag/proto/raw", exist_ok=True)
    pid = proc.pid
    def cpu_s():
        t = open(f"/proc/{pid}/stat").read().rsplit(")", 1)[1].split()
        return (int(t[11]) + int(t[12])) / os.sysconf("SC_CLK_TCK")
    for ep in ENDPOINTS:
        tag = f"{label}-{ep.replace(' ', '_')}"
        for p in bench.spawn(hosts, users, eps[ep], 32, 6, "/tmp/perf-diag/proto/raw", "w" + tag): p.wait()
        c0, t0 = cpu_s(), time.time()
        procs = bench.spawn(hosts, users, eps[ep], 32, 15, "/tmp/perf-diag/proto/raw", tag)
        for p in procs: p.wait()
        c1, t1 = cpu_s(), time.time()
        a = bench.aggregate([bench.parse_oha(f"/tmp/perf-diag/proto/raw/{tag}-{i}.json") for i in range(len(procs))])
        res[ep] = {"rps": a["rps"], "p50_ms": a["p50_ms"], "errors": a["errors"], "cores": round((c1 - c0) / (t1 - t0), 2),
                   "cpu_ms_per_req": round((c1 - c0) * 1000 / max(a["requests"], 1), 2)}
        print(label, ep, res[ep], flush=True)
    json.dump(res, open(f"/tmp/perf-diag/proto/result-{label}.json", "w"), indent=1)
finally:
    if proc: proc.send_signal(signal.SIGTERM)
    kc.terminate()
    bench.set_limits(restore=old)
    for b in ("java", "rust"): bench.kubectl("scale", f"deploy/{bench.APP[b]}", "--replicas=1", check=False)
print("proto done", label, flush=True)
