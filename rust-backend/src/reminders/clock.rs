//! Injectable clock: the scheduler and the rule engine never read the system time directly,
//! so tests can advance time (Java: `java.time.Clock` produced in `config/`).

use chrono::{DateTime, Duration, Utc};
use std::sync::Mutex;

pub trait Clock: Send + Sync {
    fn now(&self) -> DateTime<Utc>;
}

/// Wall-clock time
pub struct SystemClock;

impl Clock for SystemClock {
    fn now(&self) -> DateTime<Utc> {
        Utc::now()
    }
}

/// Clock that only moves when told to
pub struct FakeClock {
    now: Mutex<DateTime<Utc>>,
}

impl FakeClock {
    pub fn new(now: DateTime<Utc>) -> Self {
        Self {
            now: Mutex::new(now),
        }
    }

    pub fn set(&self, now: DateTime<Utc>) {
        *self.now.lock().expect("fake clock lock") = now;
    }

    pub fn advance(&self, by: Duration) {
        *self.now.lock().expect("fake clock lock") += by;
    }
}

impl Clock for FakeClock {
    fn now(&self) -> DateTime<Utc> {
        *self.now.lock().expect("fake clock lock")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fake_clock_moves_only_when_told() {
        let start = DateTime::parse_from_rfc3339("2026-01-01T10:00:00Z")
            .unwrap()
            .with_timezone(&Utc);
        let clock = FakeClock::new(start);
        assert_eq!(clock.now(), start);
        clock.advance(Duration::minutes(15));
        assert_eq!(clock.now(), start + Duration::minutes(15));
        clock.set(start);
        assert_eq!(clock.now(), start);
    }
}
