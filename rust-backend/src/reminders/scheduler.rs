//! Reminder scheduler (Java `AppointmentReminderScheduler`: cron `0 */15 * * * ?` on the leader).

use crate::appointments::repository::{AppointmentRepository, RepositoryError};
use crate::reminders::clock::Clock;
use crate::reminders::leader::LeaderElection;
use crate::reminders::services::ReminderRuleEngine;
use chrono::{DateTime, Duration, DurationRound, Utc};
use std::sync::Arc;
use tracing::{debug, error};

/// Run interval; ticks happen at minutes 0, 15, 30, 45
const TICK_MINUTES: i64 = 15;
/// Appointments are considered up to 20 weeks ahead (Java: `20 * 7` days)
const LOOKAHEAD_DAYS: i64 = 20 * 7;

pub struct ReminderScheduler {
    appointments: Arc<AppointmentRepository>,
    engine: ReminderRuleEngine,
    election: Arc<LeaderElection>,
    clock: Arc<dyn Clock>,
}

impl ReminderScheduler {
    pub fn new(
        appointments: Arc<AppointmentRepository>,
        engine: ReminderRuleEngine,
        election: Arc<LeaderElection>,
        clock: Arc<dyn Clock>,
    ) -> Self {
        Self {
            appointments,
            engine,
            election,
            clock,
        }
    }

    /// One scheduler run (Java `sendPendingReminders`). Returns `None` when this instance is not
    /// the leader, else the number of fired reminders.
    pub async fn run_once(&self) -> Result<Option<usize>, RepositoryError> {
        if !self.election.is_leader().await {
            debug!("Not the scheduler leader, skipping reminder run");
            return Ok(None);
        }
        let now = self.clock.now();
        let appointments = self
            .appointments
            .find_non_cancelled_between(now, now + Duration::days(LOOKAHEAD_DAYS))
            .await?;
        Ok(Some(self.engine.evaluate(&appointments, now).await))
    }

    /// Runs forever, once per quarter hour
    pub async fn run(&self) {
        loop {
            let now = self.clock.now();
            let wait = (next_tick_after(now) - now)
                .to_std()
                .unwrap_or(std::time::Duration::ZERO);
            tokio::time::sleep(wait).await;
            if let Err(e) = self.run_once().await {
                error!("Reminder run failed: {}", e);
            }
        }
    }
}

/// The next quarter-hour boundary strictly after `now`
pub fn next_tick_after(now: DateTime<Utc>) -> DateTime<Utc> {
    let tick = Duration::minutes(TICK_MINUTES);
    now.duration_trunc(tick).expect("quarter hours fit") + tick
}

#[cfg(test)]
mod tests {
    use super::*;

    fn at(date: &str) -> DateTime<Utc> {
        DateTime::parse_from_rfc3339(date)
            .unwrap()
            .with_timezone(&Utc)
    }

    #[test]
    fn next_tick_is_the_next_quarter_hour() {
        assert_eq!(
            next_tick_after(at("2026-01-01T10:07:30Z")),
            at("2026-01-01T10:15:00Z")
        );
        assert_eq!(
            next_tick_after(at("2026-01-01T10:45:00Z")),
            at("2026-01-01T11:00:00Z")
        );
        assert_eq!(
            next_tick_after(at("2026-01-01T23:59:59Z")),
            at("2026-01-02T00:00:00Z")
        );
    }
}
