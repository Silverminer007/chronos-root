import json, os, subprocess, sys, time
sys.path.insert(0, "/tmp/perf-diag/tools")
import bench
bench.KC_PORT = 18082
backend, cpu = sys.argv[1], sys.argv[2]
bench.LIMITS = {"cpu": cpu, "memory": "2Gi"}
kc = subprocess.Popen(["kubectl", "-n", bench.NS, "port-forward", "svc/keycloak", "18082:8080"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(3)
res = {}
old = None
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
    cg = bench.cgroup(cid)
    users = bench.seed(ip, False)
    hosts = {"ip": ip, "api": 8080, "mgmt": mgmt}
    eps = {e[0]: e for e in bench.endpoints(users[0])}
    os.makedirs("/tmp/perf-diag/scal", exist_ok=True)
    def stat():
        d = dict(l.split() for l in open(f"{cg}/cpu.stat"))
        return int(d["usage_usec"]), int(d["nr_throttled"]), int(d["throttled_usec"])
    for ep in ("get appointment", "get user"):
        for p in bench.spawn(hosts, users, eps[ep], 32, 8, "/tmp/perf-diag/scal", "warm"): p.wait()
        for c in (1, 2, 4, 8, 16, 32, 64):
            s0, t0 = stat(), time.time()
            procs = bench.spawn(hosts, users, eps[ep], c, 8, "/tmp/perf-diag/scal", f"{ep.replace(' ', '_')}{c}")
            for p in procs: p.wait()
            s1, t1 = stat(), time.time()
            a = bench.aggregate([bench.parse_oha(f"/tmp/perf-diag/scal/{ep.replace(' ', '_')}{c}-{i}.json") for i in range(len(procs))])
            dt = t1 - t0
            res.setdefault(ep, {})[str(c)] = {"rps": a["rps"], "p50_ms": a["p50_ms"], "cores": round((s1[0] - s0[0]) / 1e6 / dt, 2),
                                              "throttled_s": round((s1[2] - s0[2]) / 1e6, 2), "throttled_periods": s1[1] - s0[1]}
            print(backend, cpu, ep, c, res[ep][str(c)], flush=True)
    json.dump(res, open(f"/tmp/perf-diag/scaling-{backend}-{cpu}.json", "w"), indent=1)
finally:
    kc.terminate()
    if old: bench.set_limits(restore=old)
    for b in ("java", "rust"): bench.kubectl("scale", f"deploy/{bench.APP[b]}", "--replicas=1", check=False)
print("scaling done", flush=True)
