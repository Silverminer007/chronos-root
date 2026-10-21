import json, os, subprocess, sys, time
sys.path.insert(0, os.path.expanduser("~/git/chronos-root/deployment/parity/perf"))
import bench
bench.KC_PORT = 18082
kc = subprocess.Popen(["kubectl", "-n", bench.NS, "port-forward", "svc/keycloak", "18082:8080"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(3)
def pg(sql, db):
    return bench.kubectl("exec", "deploy/postgres", "--", "psql", "-U", "postgres", "-d", db, "-Atc", sql)
out = {}
try:
    for backend in ("java", "rust"):
        ip, _, _ = bench.pod(backend)
        db = bench.DB[backend]
        tok = bench.kc_token("perf-user-1")
        api = bench.Client(ip, 8080, tok)
        s, lst = api.json("GET", "/api/v2/appointments?page=0&size=10")
        appts = lst["data"] if isinstance(lst, dict) else lst
        a = appts[0]["id"]
        cases = {
            "get user": ("GET", "/api/v2/user", None),
            "list appointments": ("GET", "/api/v2/appointments?page=0&size=10", None),
            "get appointment": ("GET", f"/api/v2/appointments/{a}", None),
            "list messages": ("GET", f"/api/v2/appointments/{a}/messages", None),
            "create appointment": ("POST", "/api/v2/appointments", bench.APPOINTMENT),
            "send message": ("POST", f"/api/v2/appointments/{a}/messages", {"body": "x"}),
        }
        for name, (m, p, b) in cases.items():
            for _ in range(5):
                api.call(m, p, b)
            q = "select xact_commit, tup_returned, tup_fetched, tup_inserted, tup_updated from pg_stat_database where datname='%s'" % db
            time.sleep(1.5)
            a0 = [int(x) for x in pg(q, db).split("|")]
            n = 100
            for _ in range(n):
                api.call(m, p, b)
            time.sleep(1.5)
            a1 = [int(x) for x in pg(q, db).split("|")]
            d = [(y - x) / n for x, y in zip(a0, a1)]
            out.setdefault(name, {})[backend] = {"transactions": round(d[0], 2), "rows_returned": round(d[1], 1), "rows_fetched": round(d[2], 1), "rows_inserted": round(d[3], 1), "rows_updated": round(d[4], 1)}
            print(name, backend, out[name][backend], flush=True)
    json.dump(out, open("/tmp/perf-diag/txn-count.json", "w"), indent=1)
finally:
    kc.terminate()
print("txn count done", flush=True)
