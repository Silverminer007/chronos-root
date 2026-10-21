import os, subprocess, sys, time
sys.path.insert(0, os.path.expanduser("~/git/chronos-root/deployment/parity/perf"))
import bench
bench.KC_PORT = 18082
kc = subprocess.Popen(["kubectl", "-n", bench.NS, "port-forward", "svc/keycloak", "18082:8080"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(3)
old = bench.set_limits()
try:
    bench.ensure_users(16)
    bench.scale(bench.APP["java"], 0)
    bench.scale(bench.APP["rust"], 0)
    bench.reset_db("rust")
    bench.kubectl("scale", f"deploy/{bench.APP['rust']}", "--replicas=1")
    ip, cid, _ = bench.pod("rust")
    bench.wait_ready(ip, 8080)
    cg = bench.cgroup(cid)
    pid = int(open(f"{cg}/cgroup.procs").read().split()[0])
    users = bench.seed(ip, False)
    hosts = {"ip": ip, "api": 8080, "mgmt": 8080}
    eps = {e[0]: e for e in bench.endpoints(users[0])}
    which = sys.argv[1] if len(sys.argv) > 1 else "list appointments"
    os.makedirs("/tmp/perf-diag/prof", exist_ok=True)
    warm = bench.spawn(hosts, users, eps[which], 32, 10, "/tmp/perf-diag/prof", "warm")
    for p in warm: p.wait()
    procs = bench.spawn(hosts, users, eps[which], 32, 25, "/tmp/perf-diag/prof", "load")
    time.sleep(4)
    subprocess.run(["sudo", "-n", "perf", "record", "-F", "499", "-g", "-p", str(pid), "-o", "/tmp/perf-diag/prof/perf.data", "--", "sleep", "12"], capture_output=True)
    for p in procs: p.wait()
    print("profiled pid", pid, "endpoint", which, flush=True)
finally:
    kc.terminate()
    bench.set_limits(restore=old)
    for b in ("java", "rust"):
        bench.kubectl("scale", f"deploy/{bench.APP[b]}", "--replicas=1", check=False)
