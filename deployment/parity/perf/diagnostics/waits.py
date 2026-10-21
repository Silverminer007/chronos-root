import collections, json, os, subprocess, sys, threading, time
sys.path.insert(0, "/tmp/perf-diag/tools")
import bench
bench.KC_PORT = 18082
backend = sys.argv[1] if len(sys.argv) > 1 else "rust"
db = bench.DB[backend]
ENDPOINTS = sys.argv[2].split(",") if len(sys.argv) > 2 else ["get user", "get appointment", "list appointments", "send message"]
kc = subprocess.Popen(["kubectl", "-n", bench.NS, "port-forward", "svc/keycloak", "18082:8080"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(3)
def psql(sql, d="postgres"):
    return bench.kubectl("exec", "deploy/postgres", "--", "psql", "-U", "postgres", "-d", d, "-Atc", sql)

SQL = ("select state, coalesce(wait_event_type,'-'), coalesce(wait_event,'-'), count(*) from pg_stat_activity "
       "where datname='%s' and pid <> pg_backend_pid() and backend_type='client backend' group by 1,2,3 \\watch 0.1\n" % db)

def sample(seconds):
    p = subprocess.Popen(["kubectl", "-n", bench.NS, "exec", "-i", "deploy/postgres", "--", "psql", "-U", "postgres", "-d", "postgres", "-At", "-F", "|"],
                         stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    p.stdin.write(SQL); p.stdin.flush()
    hist, samples, cur = collections.Counter(), 0, collections.Counter()
    end = time.time() + seconds
    lines = []
    def reader():
        for l in p.stdout:
            lines.append(l.rstrip("\n"))
    t = threading.Thread(target=reader, daemon=True); t.start()
    time.sleep(seconds)
    p.terminate()
    for l in lines:
        parts = l.split("|")
        if len(parts) == 4 and parts[3].isdigit():
            hist[(parts[0], parts[1], parts[2])] += int(parts[3])
        elif l.startswith("Watch") or l.startswith("(") : samples += 0
    # number of samples = number of watch headers; approximate with total / typical
    return hist

res = {}
try:
    old = bench.set_limits()
    bench.ensure_users(16)
    other = "java" if backend == "rust" else "rust"
    bench.scale(bench.APP[other], 0); bench.scale(bench.APP[backend], 0)
    bench.reset_db(backend)
    bench.kubectl("scale", f"deploy/{bench.APP[backend]}", "--replicas=1")
    ip, cid, _ = bench.pod(backend)
    mgmt = 9000 if backend == "java" else 8080
    bench.wait_ready(ip, mgmt)
    users = bench.seed(ip, False)
    hosts = {"ip": ip, "api": 8080, "mgmt": mgmt}
    eps = {e[0]: e for e in bench.endpoints(users[0])}
    os.makedirs("/tmp/perf-diag/waits", exist_ok=True)
    for mode in ("sync on", "sync off"):
        psql("ALTER SYSTEM SET synchronous_commit = %s" % ("on" if mode == "sync on" else "off")); psql("SELECT pg_reload_conf()")
        time.sleep(1)
        for ep in ENDPOINTS:
            tag = (mode + ep).replace(" ", "_")
            for p in bench.spawn(hosts, users, eps[ep], 32, 6, "/tmp/perf-diag/waits", "w" + tag): p.wait()   # warm
            procs = bench.spawn(hosts, users, eps[ep], 32, 14, "/tmp/perf-diag/waits", tag)
            time.sleep(3)
            hist = sample(8)
            for p in procs: p.wait()
            a = bench.aggregate([bench.parse_oha(f"/tmp/perf-diag/waits/{tag}-{i}.json") for i in range(len(procs))])
            total = sum(hist.values()) or 1
            top = [(f"{s}/{t}/{w}", round(100 * c / total, 1)) for (s, t, w), c in hist.most_common(6)]
            res.setdefault(ep, {})[mode] = {"rps": a["rps"], "p50_ms": a["p50_ms"], "pg_connections_avg": round(total / max(1, 80), 1), "pg_states_percent": top}
            print(ep, mode, res[ep][mode], flush=True)
    json.dump(res, open(f"/tmp/perf-diag/waits-{backend}.json", "w"), indent=1)
finally:
    psql("ALTER SYSTEM RESET synchronous_commit"); psql("SELECT pg_reload_conf()")
    kc.terminate()
    bench.set_limits(restore=old)
    for b in ("java", "rust"): bench.kubectl("scale", f"deploy/{bench.APP[b]}", "--replicas=1", check=False)
print("waits done", flush=True)
