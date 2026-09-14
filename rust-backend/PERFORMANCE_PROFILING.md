# Performance Profiling & Memory Optimization

This document describes the performance profiling setup, baseline measurements, and optimization work for the Chronos Rust backend.

## Performance Targets

- **Heap usage**: < 50 MiB under normal load
- **`/api/v2/appointments` p95 latency**: < 300ms
- **All other endpoints p95 latency**: < 1s
- **Connection pool**: Optimized for memory efficiency
- **Queries**: N+1 queries eliminated, indexes verified

## 1. Heap Profiling Setup

### Using `valgrind` (Linux)

```bash
# Install valgrind
sudo apt-get install valgrind

# Run server with Valgrind's Massif profiler
valgrind --tool=massif --massif-out-file=massif.out \
  ./target/release/chronos_date_api

# View heap profile
ms_print massif.out > heap_profile.txt
```

### Using `heaptrack` (Linux, GUI)

```bash
# Install heaptrack
sudo apt-get install heaptrack heaptrack-gui

# Profile the binary
heaptrack ./target/release/chronos_date_api

# View GUI
heaptrack_gui heaptrack.chronos_date_api.PID.gz
```

### Using `perf` (Linux)

```bash
# Build with debug symbols
cargo build --release

# Profile with perf
perf record -g --call-graph=dwarf ./target/release/chronos_date_api

# Analyze
perf report
```

## 2. Load Testing Setup

### Using k6

k6 is a modern load testing tool with excellent support for realistic scenarios.

**Installation:**

```bash
# Ubuntu/Debian
sudo apt-get install k6

# macOS
brew install k6
```

**Running the load test:**

```bash
# Run baseline load test
k6 run benchmarks/load_test.k6.js

# Run with custom configuration
k6 run -u 100 -d 60s benchmarks/load_test.k6.js

# Output in JSON format for analysis
k6 run --out json=results.json benchmarks/load_test.k6.js

# Run with specific VU (virtual users) count
k6 run --vus 50 --duration 30s benchmarks/load_test.k6.js
```

### Using `wrk` (alternative)

```bash
# Install wrk
git clone https://github.com/wg/wrk.git
cd wrk && make

# Simple load test
./wrk -t4 -c100 -d30s http://localhost:8080/api/v2/appointments

# With custom script for more realistic scenarios
./wrk -t4 -c100 -d30s -s script.lua http://localhost:8080/api/v2/appointments
```

## 3. Baseline Measurements

### Running a baseline test

```bash
# Terminal 1: Start the backend
cd rust-backend
cargo run --release

# Terminal 2: Run baseline profiling
k6 run --out json=baseline_before.json benchmarks/load_test.k6.js

# Terminal 3: Monitor heap usage
heaptrack ./target/release/chronos_date_api
```

### Metrics to record

1. **Memory Usage**
   - Peak heap size (MB)
   - Average heap size (MB)
   - Growth rate over time

2. **Latency (p50/p95/p99)**
   - Per endpoint
   - Under different load levels (10 VU, 50 VU, 100 VU)

3. **Throughput**
   - Requests per second
   - Success rate (%)

### Example baseline template

```
=== BASELINE BEFORE OPTIMIZATION ===

Memory:
  Peak heap: __ MB
  Avg heap: __ MB
  Growth: __ MB per 1000 requests

Latency (p95):
  GET /api/v2/appointments: __ ms
  GET /api/v2/appointments/:id: __ ms
  GET /q/health/live: __ ms

Throughput:
  Requests/sec: __
  Success rate: __ %

Observations:
- [List any notable findings]
```

## 4. Optimization Work

### Phase 1: Connection Pool Tuning

**Current configuration** (in `src/database/mod.rs`):

```rust
let pool = PgPoolOptions::new()
    .max_connections(20)
    .min_connections(5)
    .connect_timeout(Duration::from_secs(30))
    .idle_timeout(Some(Duration::from_secs(600)))
    .create_pool(&database_url)
    .await?;
```

**Optimization steps:**

1. Reduce `max_connections` to match workload (e.g., 10-15 for light load)
2. Adjust `idle_timeout` to recycle connections faster
3. Monitor connection usage under load with:
   ```rust
   // Log connection pool stats periodically
   let pool_size = pool.size();
   let active = pool.num_active();
   tracing::info!("Pool: {} active/{} max", active, pool_size);
   ```

### Phase 2: Query Optimization

**Identify N+1 queries:**

1. Enable SQL logging in debug mode:
   ```rust
   // In main.rs or tests
   std::env::set_var("RUST_LOG", "sqlx=debug");
   ```

2. Run integration tests with logging to spot N+1 patterns

**Optimize appointments list endpoint:**

- [ ] Add database indexes on foreign keys
- [ ] Use JOIN queries instead of separate queries
- [ ] Add pagination to limit results per request
- [ ] Cache metadata (e.g., participant counts)

### Phase 3: Tokio Task Spawning

**Check for excessive task spawning:**

1. Review `appointments/services/mod.rs` for unnecessary `tokio::spawn` calls
2. Ensure background tasks are properly bounded (use task limits)
3. Profile task count with:
   ```rust
   tracing::debug!("Spawned task, current count: {}", /* task counter */);
   ```

### Phase 4: Allocator Tuning

**Switch to a more efficient allocator** (optional):

Add to `Cargo.toml`:

```toml
[target.x86_64-unknown-linux-musl]
profile.release.opt-level = 3
profile.release.lto = true
profile.release.codegen-units = 1

[dependencies]
# Use mimalloc for better performance
mimalloc = { version = "0.1", optional = true }

[features]
default = ["mimalloc"]
```

Use in `main.rs`:

```rust
#[cfg(feature = "mimalloc")]
#[global_allocator]
static GLOBAL: mimalloc::MiMalloc = mimalloc::MiMalloc;
```

## 5. Post-Optimization Measurements

After each optimization phase, re-run the load test and compare:

```
=== BASELINE AFTER OPTIMIZATION ===

Memory:
  Peak heap: __ MB (was: __ MB) → __ % improvement
  Avg heap: __ MB (was: __ MB) → __ % improvement

Latency (p95):
  GET /api/v2/appointments: __ ms (was: __ ms) → __ % improvement
  ...

Throughput:
  Requests/sec: __ (was: __) → __ % improvement
```

## 6. CI/CD Integration

### GitHub Actions Workflow

Add to `.github/workflows/rust-backend-perf.yml`:

```yaml
name: Performance Regression Tests

on:
  pull_request:
    paths:
      - 'rust-backend/**'

jobs:
  benchmark:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_DB: chronos
          POSTGRES_USER: chronos
          POSTGRES_PASSWORD: chronos
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

    steps:
      - uses: actions/checkout@v3
      - uses: actions-rs/toolchain@v1
        with:
          toolchain: stable
      - name: Run load tests
        run: |
          cd rust-backend
          cargo build --release
          k6 run --out json=results.json benchmarks/load_test.k6.js
```

## 7. Troubleshooting

### "Heap size is high even after optimization"

1. Check for memory leaks:
   ```bash
   valgrind --leak-check=full --show-leak-kinds=all ./target/release/chronos_date_api
   ```

2. Profile with heaptrack to identify largest allocations

3. Check database query results (are we fetching too much data?)

### "p95 latency spikes during load test"

1. Check if garbage collection is pausing the runtime:
   - Rust has no GC, but allocator fragmentation can cause pauses
   - Try different allocators (mimalloc, jemalloc)

2. Check database connection wait times:
   - Monitor connection pool exhaustion
   - Adjust pool size or add connection pooling middleware

3. Check for lock contention:
   - Use `parking_lot` for better performance on mutexes
   - Avoid holding locks across await points

## 8. Documentation and Reporting

After completing all optimization phases, document:

1. **Baseline Report** — Initial measurements before optimization
2. **Optimization Steps** — Each change made and why
3. **Final Report** — Measurements after optimization
4. **Recommendations** — Future improvements

See `PERFORMANCE_REPORT.md` for the final report.
