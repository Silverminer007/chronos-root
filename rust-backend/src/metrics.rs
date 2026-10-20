//! Prometheus metrics with the series names and labels Micrometer exposes in the Java backend
//! (ADR-2026-10-20-020).
//!
//! HTTP server requests are recorded as `http_server_requests_seconds_{count,sum,max,bucket}` with the
//! labels `exception`, `method`, `outcome`, `status`, `uri`. The registry is hand-rolled: it needs one
//! histogram family, a few gauges and exact control over the label set.

use axum::{
    extract::{MatchedPath, Request, State},
    http::{header, StatusCode},
    middleware::Next,
    response::{IntoResponse, Response},
};
use sqlx::PgPool;
use std::collections::BTreeMap;
use std::fmt::Write;
use std::sync::{Arc, Mutex};
use std::time::{Instant, SystemTime, UNIX_EPOCH};

/// Upper bounds (seconds) of the latency histogram buckets.
const BUCKETS: [f64; 11] = [
    0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0,
];

/// Micrometer's `uri` tag for requests that matched no route.
const URI_NOT_FOUND: &str = "NOT_FOUND";

/// Java: `quarkus.micrometer.binder.http-server.ignore-patterns=^\/metrics$`
const METRICS_PATH: &str = "/metrics";

const PROMETHEUS_CONTENT_TYPE: &str = "text/plain; version=0.0.4; charset=utf-8";

#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
struct RequestKey {
    method: String,
    uri: String,
    status: u16,
}

#[derive(Default)]
struct Histogram {
    count: u64,
    sum: f64,
    max: f64,
    /// Cumulative counts per entry of [`BUCKETS`].
    buckets: [u64; BUCKETS.len()],
}

impl Histogram {
    fn observe(&mut self, seconds: f64) {
        self.count += 1;
        self.sum += seconds;
        self.max = self.max.max(seconds);
        for (bucket, bound) in self.buckets.iter_mut().zip(BUCKETS) {
            if seconds <= bound {
                *bucket += 1;
            }
        }
    }
}

pub struct Metrics {
    requests: Mutex<BTreeMap<RequestKey, Histogram>>,
    started: Instant,
    start_time_seconds: f64,
}

impl Default for Metrics {
    fn default() -> Self {
        Self {
            requests: Mutex::new(BTreeMap::new()),
            started: Instant::now(),
            start_time_seconds: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map_or(0.0, |d| d.as_secs_f64()),
        }
    }
}

/// Micrometer's `outcome` tag derived from the status code.
fn outcome(status: u16) -> &'static str {
    match status {
        100..=199 => "INFORMATIONAL",
        200..=299 => "SUCCESS",
        300..=399 => "REDIRECTION",
        400..=499 => "CLIENT_ERROR",
        500..=599 => "SERVER_ERROR",
        _ => "UNKNOWN",
    }
}

/// axum route templates use `:id`, Micrometer/Quarkus use `{id}`.
fn micrometer_uri(axum_template: &str) -> String {
    axum_template
        .split('/')
        .map(|segment| match segment.strip_prefix(':') {
            Some(name) => format!("{{{name}}}"),
            None => segment.to_string(),
        })
        .collect::<Vec<_>>()
        .join("/")
}

fn escape_label(value: &str) -> String {
    value
        .replace('\\', "\\\\")
        .replace('"', "\\\"")
        .replace('\n', "\\n")
}

impl Metrics {
    pub fn record_request(&self, method: &str, uri: &str, status: u16, seconds: f64) {
        let key = RequestKey {
            method: method.to_string(),
            uri: uri.to_string(),
            status,
        };
        if let Ok(mut requests) = self.requests.lock() {
            requests.entry(key).or_default().observe(seconds);
        }
    }

    /// Render all series in the Prometheus text exposition format.
    pub fn render(&self, pool: &PgPool) -> String {
        let mut out = String::new();
        self.render_http(&mut out);
        self.render_process(&mut out);
        render_pool(&mut out, pool);
        out
    }

    fn render_http(&self, out: &mut String) {
        let Ok(requests) = self.requests.lock() else {
            return;
        };
        let name = "http_server_requests_seconds";
        let labels = |k: &RequestKey| {
            format!(
                "exception=\"none\",method=\"{}\",outcome=\"{}\",status=\"{}\",uri=\"{}\"",
                escape_label(&k.method),
                outcome(k.status),
                k.status,
                escape_label(&k.uri)
            )
        };
        let _ = writeln!(out, "# HELP {name} HTTP server request latency");
        let _ = writeln!(out, "# TYPE {name} histogram");
        for (key, h) in requests.iter() {
            let l = labels(key);
            for (bound, count) in BUCKETS.iter().zip(h.buckets) {
                let _ = writeln!(out, "{name}_bucket{{{l},le=\"{bound}\"}} {count}");
            }
            let _ = writeln!(out, "{name}_bucket{{{l},le=\"+Inf\"}} {}", h.count);
            let _ = writeln!(out, "{name}_count{{{l}}} {}", h.count);
            let _ = writeln!(out, "{name}_sum{{{l}}} {}", h.sum);
        }
        let _ = writeln!(
            out,
            "# HELP {name}_max Maximum observed HTTP server request latency"
        );
        let _ = writeln!(out, "# TYPE {name}_max gauge");
        for (key, h) in requests.iter() {
            let _ = writeln!(out, "{name}_max{{{}}} {}", labels(key), h.max);
        }
    }

    fn render_process(&self, out: &mut String) {
        gauge(
            out,
            "process_uptime_seconds",
            "The uptime of the process",
            "",
            self.started.elapsed().as_secs_f64(),
        );
        gauge(
            out,
            "process_start_time_seconds",
            "Start time of the process since unix epoch",
            "",
            self.start_time_seconds,
        );
        let cpus = std::thread::available_parallelism().map_or(1, usize::from);
        gauge(
            out,
            "system_cpu_count",
            "The number of processors available",
            "",
            cpus as f64,
        );
        if let Some(rss) = resident_memory_bytes() {
            gauge(
                out,
                "process_resident_memory_bytes",
                "Resident memory size in bytes",
                "",
                rss as f64,
            );
        }
    }
}

/// `VmRSS` from `/proc/self/status`; `None` where procfs is unavailable.
fn resident_memory_bytes() -> Option<u64> {
    let status = std::fs::read_to_string("/proc/self/status").ok()?;
    let kib: u64 = status
        .lines()
        .find_map(|l| l.strip_prefix("VmRSS:"))?
        .trim()
        .trim_end_matches("kB")
        .trim()
        .parse()
        .ok()?;
    Some(kib * 1024)
}

fn gauge(out: &mut String, name: &str, help: &str, labels: &str, value: f64) {
    let _ = writeln!(out, "# HELP {name} {help}");
    let _ = writeln!(out, "# TYPE {name} gauge");
    if labels.is_empty() {
        let _ = writeln!(out, "{name} {value}");
    } else {
        let _ = writeln!(out, "{name}{{{labels}}} {value}");
    }
}

/// DB pool gauges, named like Quarkus' Agroal metrics.
fn render_pool(out: &mut String, pool: &PgPool) {
    let size = pool.size() as usize;
    let idle = pool.num_idle();
    let labels = "datasource=\"default\"";
    gauge(
        out,
        "agroal_active_count",
        "Number of active connections",
        labels,
        size.saturating_sub(idle) as f64,
    );
    gauge(
        out,
        "agroal_available_count",
        "Number of idle connections available",
        labels,
        idle as f64,
    );
    gauge(
        out,
        "agroal_max_size",
        "Maximum number of connections in the pool",
        labels,
        f64::from(pool.options().get_max_connections()),
    );
}

/// Records every request except `/metrics` itself.
pub async fn track_requests(
    State(metrics): State<Arc<Metrics>>,
    request: Request,
    next: Next,
) -> Response {
    let method = request.method().to_string();
    let uri = request
        .extensions()
        .get::<MatchedPath>()
        .map_or_else(|| URI_NOT_FOUND.to_string(), |p| micrometer_uri(p.as_str()));
    let start = Instant::now();
    let response = next.run(request).await;
    if uri != METRICS_PATH {
        metrics.record_request(
            &method,
            &uri,
            response.status().as_u16(),
            start.elapsed().as_secs_f64(),
        );
    }
    response
}

pub async fn metrics_handler(
    State((metrics, pool)): State<(Arc<Metrics>, PgPool)>,
) -> impl IntoResponse {
    (
        StatusCode::OK,
        [(header::CONTENT_TYPE, PROMETHEUS_CONTENT_TYPE)],
        metrics.render(&pool),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn outcome_matches_micrometer() {
        assert_eq!(outcome(204), "SUCCESS");
        assert_eq!(outcome(302), "REDIRECTION");
        assert_eq!(outcome(404), "CLIENT_ERROR");
        assert_eq!(outcome(503), "SERVER_ERROR");
    }

    #[test]
    fn uri_template_uses_curly_braces() {
        assert_eq!(
            micrometer_uri("/api/v2/appointments/:id/participants/:userId"),
            "/api/v2/appointments/{id}/participants/{userId}"
        );
        assert_eq!(micrometer_uri("/q/health/live"), "/q/health/live");
    }

    #[test]
    fn histogram_buckets_are_cumulative() {
        let mut h = Histogram::default();
        h.observe(0.004);
        h.observe(0.2);
        assert_eq!(h.count, 2);
        assert_eq!(h.buckets[0], 1); // <= 0.005
        assert_eq!(h.buckets[5], 2); // <= 0.25
        assert!((h.max - 0.2).abs() < f64::EPSILON);
    }

    #[test]
    fn label_values_are_escaped() {
        assert_eq!(escape_label("a\"b\\c\nd"), "a\\\"b\\\\c\\nd");
    }
}
