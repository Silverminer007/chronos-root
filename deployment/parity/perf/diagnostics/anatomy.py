import json, os, re, subprocess, sys, time
sys.path.insert(0, "/tmp/perf-diag/tools")
import bench
bench.KC_PORT = 18082
kc = subprocess.Popen(["kubectl", "-n", bench.NS, "port-forward", "svc/keycloak", "18082:8080"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(3)
def psql(db, sql):
    return bench.kubectl("exec", "deploy/postgres", "--", "psql", "-U", "postgres", "-d", db, "-Atc", sql)
out = {}
try:
    bench.ensure_users(2)
    psql("postgres", "ALTER SYSTEM SET log_statement='all'")
    psql("postgres", "SELECT pg_reload_conf()")
    time.sleep(2)
    for backend in ("java", "rust"):
        ip, _, _ = bench.pod(backend)
        db = bench.DB[backend]
        api = bench.Client(ip, 8080, bench.kc_token("perf-user-1"))
        api.call("GET", "/api/v2/user")
        s, a = api.json("POST", "/api/v2/appointments", {**bench.APPOINTMENT, "name": "Anatomie"})
        appt = a["id"]
        cases = [("get user", "GET", "/api/v2/user", None),
                 ("get appointment", "GET", f"/api/v2/appointments/{appt}", None),
                 ("list appointments", "GET", "/api/v2/appointments?page=0&size=10", None),
                 ("list messages", "GET", f"/api/v2/appointments/{appt}/messages", None),
                 ("create appointment", "POST", "/api/v2/appointments", bench.APPOINTMENT),
                 ("send message", "POST", f"/api/v2/appointments/{appt}/messages", {"body": "x"})]
        for name, m, p, b in cases:
            for _ in range(3):
                api.call(m, p, b)          # warm up (prepared statements are logged as parse/bind only once)
            time.sleep(1)
            psql(db, f"select 'MARK_START {name}'")
            api.call(m, p, b)
            time.sleep(0.5)
            psql(db, f"select 'MARK_END {name}'")
            time.sleep(0.5)
            log = bench.kubectl("logs", "deploy/postgres", "--since=20s")
            lines = [l for l in log.splitlines() if "LOG:  " in l and ("execute" in l or "statement:" in l)]
            stmts, on = [], False
            for l in lines:
                txt = re.sub(r"^.*?(execute [^:]*|statement): ", "", l)
                if f"MARK_START {name}" in txt: on = True; continue
                if f"MARK_END {name}" in txt: break
                if on: stmts.append(re.sub(r"\s+", " ", txt)[:130])
            out.setdefault(name, {})[backend] = stmts
            print(f"{backend:5s} {name:20s} {len(stmts):3d} statements", flush=True)
    json.dump(out, open("/tmp/perf-diag/anatomy.json", "w"), indent=1)
finally:
    psql("postgres", "ALTER SYSTEM RESET log_statement")
    psql("postgres", "SELECT pg_reload_conf()")
    kc.terminate()
print("anatomy done", flush=True)
