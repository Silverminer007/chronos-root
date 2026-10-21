import json, os, subprocess, sys, time
sys.path.insert(0, os.path.expanduser("~/git/chronos-root/deployment/parity/perf"))
import bench
bench.KC_PORT = 18082
backend = sys.argv[1] if len(sys.argv) > 1 else "rust"
kc = subprocess.Popen(["kubectl", "-n", bench.NS, "port-forward", "svc/keycloak", "18082:8080"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(3)
old = bench.set_limits()
res = {}
try:
    bench.ensure_users(16)
    other = "java" if backend == "rust" else "rust"
    bench.scale(bench.APP[other], 0)
    bench.scale(bench.APP[backend], 0)
    bench.reset_db(backend)
    bench.kubectl("scale", f"deploy/{bench.APP[backend]}", "--replicas=1")
    ip, cid, _ = bench.pod(backend)
    mgmt = 9000 if backend == "java" else 8080
    bench.wait_ready(ip, mgmt)
    cg = bench.cgroup(cid)
    pid = int(open(f"{cg}/cgroup.procs").read().split()[0])
    users = bench.seed(ip, False)
    hosts = {"ip": ip, "api": 8080, "mgmt": mgmt}
    eps = {e[0]: e for e in bench.endpoints(users[0])}
    os.makedirs("/tmp/perf-diag/pin", exist_ok=True)
    def cpu():
        return int([l for l in open(f"{cg}/cpu.stat") if l.startswith("usage_usec")][0].split()[1])
    def run(label, ep, seconds=15):
        c0, t0 = cpu(), time.time()
        procs = bench.spawn(hosts, users, eps[ep], 32, seconds, "/tmp/perf-diag/pin", label.replace(" ", "_") + ep.replace(" ", "_"))
        for p in procs: p.wait()
        c1, t1 = cpu(), time.time()
        parts = [bench.parse_oha(f"/tmp/perf-diag/pin/{label.replace(' ', '_')}{ep.replace(' ', '_')}-{i}.json") for i in range(len(procs))]
        a = bench.aggregate(parts)
        res.setdefault(ep, {})[label] = {"rps": a["rps"], "p50_ms": a["p50_ms"], "cores": round((c1 - c0) / 1e6 / (t1 - t0), 2),
                                         "cpu_ms_per_req": round((c1 - c0) / 1e3 / max(a["requests"], 1), 2)}
        print(ep, label, res[ep][label], flush=True)
    def pin(cpus):
        subprocess.run(["sudo", "-n", "taskset", "-a", "-cp", cpus, str(pid)], capture_output=True)
    for ep in ("list appointments", "get user"):
        run("warm", ep, 8)
        pin("0-13");  run("unpinned", ep)
        pin("4");     run("1 cpu", ep)
        pin("4,5");   run("2 cpus", ep)
        pin("0-13");  run("unpinned again", ep)
    json.dump(res, open(f"/tmp/perf-diag/pin/{backend}.json", "w"), indent=1)
finally:
    kc.terminate()
    bench.set_limits(restore=old)
    for b in ("java", "rust"):
        bench.kubectl("scale", f"deploy/{bench.APP[b]}", "--replicas=1", check=False)
print("pin experiment done", flush=True)
