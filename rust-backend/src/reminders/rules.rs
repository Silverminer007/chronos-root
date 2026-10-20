//! Reminder rules, copied 1:1 from the Java `application/reminder` package
//! (ADR-2026-10-20-022). The engine uses the first rule that applies to an appointment.

use crate::appointments::models::Appointment;
use chrono::{DateTime, Datelike, Duration, Utc};

/// What a rule does once one of its trigger times is due
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ReminderKind {
    /// Java `ReminderEventPort#sendReminder`
    Reminder,
    /// Java `ReminderEventPort#sendRSVPReminder`
    RsvpReminder,
}

pub trait ReminderRule: Send + Sync {
    fn name(&self) -> &str;
    fn applies_to(&self, appointment: &Appointment) -> bool;
    fn compute_trigger_times(&self, appointment: &Appointment) -> Vec<DateTime<Utc>>;
    fn kind(&self) -> ReminderKind;
}

/// The rules in the order Java produces them (`RuleConfig`): long, weekday, weekend, reminder
pub fn default_rules() -> Vec<Box<dyn ReminderRule>> {
    vec![
        Box::new(LongAppointmentRSVPRule),
        Box::new(ShortWeekdayRSVPRule),
        Box::new(ShortWeekendRSVPRule),
        Box::new(AppointmentReminderRule),
    ]
}

/// Whole hours between start and end (truncated, like `Instant#until(.., HOURS)`)
fn duration_hours(appointment: &Appointment) -> i64 {
    (appointment.end_time - appointment.start_time).num_hours()
}

/// Mon-Thu (Java `AppointmentTimeUtil#isWeekday`)
fn is_weekday(instant: DateTime<Utc>) -> bool {
    instant.weekday().number_from_monday() <= 4
}

/// Fri-Sun (Java `AppointmentTimeUtil#isWeekend`)
fn is_weekend(instant: DateTime<Utc>) -> bool {
    instant.weekday().number_from_monday() >= 5
}

/// `from`, `from + 1 day`, ... while before `until`
fn daily_between(from: DateTime<Utc>, until: DateTime<Utc>) -> Vec<DateTime<Utc>> {
    let mut times = Vec::new();
    let mut cursor = from;
    while cursor < until {
        times.push(cursor);
        cursor += Duration::days(1);
    }
    times
}

pub struct AppointmentReminderRule;

impl ReminderRule for AppointmentReminderRule {
    fn name(&self) -> &str {
        "AppointmentReminderRule"
    }

    fn applies_to(&self, _appointment: &Appointment) -> bool {
        true
    }

    fn compute_trigger_times(&self, appointment: &Appointment) -> Vec<DateTime<Utc>> {
        vec![appointment.start_time - Duration::minutes(30)]
    }

    fn kind(&self) -> ReminderKind {
        ReminderKind::Reminder
    }
}

pub struct LongAppointmentRSVPRule;

impl ReminderRule for LongAppointmentRSVPRule {
    fn name(&self) -> &str {
        "LongAppointmentRSVPRule"
    }

    fn applies_to(&self, appointment: &Appointment) -> bool {
        duration_hours(appointment) >= 24
    }

    fn compute_trigger_times(&self, appointment: &Appointment) -> Vec<DateTime<Utc>> {
        let start = appointment.start_time;
        let mut triggers = vec![
            start - Duration::weeks(16),
            start - Duration::weeks(8),
            start - Duration::weeks(4),
            start - Duration::weeks(2),
        ];
        // afterwards daily, beginning two weeks before the start
        triggers.extend(daily_between(start - Duration::weeks(2), start));
        triggers
    }

    fn kind(&self) -> ReminderKind {
        ReminderKind::RsvpReminder
    }
}

pub struct ShortWeekdayRSVPRule;

impl ReminderRule for ShortWeekdayRSVPRule {
    fn name(&self) -> &str {
        "ShortWeekdayRSVPRule"
    }

    fn applies_to(&self, appointment: &Appointment) -> bool {
        duration_hours(appointment) < 24 && is_weekday(appointment.start_time)
    }

    fn compute_trigger_times(&self, appointment: &Appointment) -> Vec<DateTime<Utc>> {
        let start = appointment.start_time;
        vec![
            start - Duration::days(7),
            start - Duration::days(4),
            start - Duration::days(2),
            start - Duration::days(1),
        ]
    }

    fn kind(&self) -> ReminderKind {
        ReminderKind::RsvpReminder
    }
}

pub struct ShortWeekendRSVPRule;

impl ReminderRule for ShortWeekendRSVPRule {
    fn name(&self) -> &str {
        "ShortWeekendRSVPRule"
    }

    fn applies_to(&self, appointment: &Appointment) -> bool {
        duration_hours(appointment) < 24 && is_weekend(appointment.start_time)
    }

    fn compute_trigger_times(&self, appointment: &Appointment) -> Vec<DateTime<Utc>> {
        let start = appointment.start_time;
        let mut triggers = vec![
            start - Duration::weeks(4),
            start - Duration::weeks(2),
            start - Duration::weeks(1),
        ];
        // afterwards daily, beginning one week before the start
        triggers.extend(daily_between(start - Duration::weeks(1), start));
        triggers
    }

    fn kind(&self) -> ReminderKind {
        ReminderKind::RsvpReminder
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::appointments::models::AppointmentStatus;

    fn at(date: &str) -> DateTime<Utc> {
        DateTime::parse_from_rfc3339(date)
            .unwrap()
            .with_timezone(&Utc)
    }

    fn appointment(start: DateTime<Utc>, end: DateTime<Utc>) -> Appointment {
        Appointment {
            id: 1,
            name: "Test".to_string(),
            description: None,
            venue: None,
            start_time: start,
            end_time: end,
            minimal_attendees: None,
            created_at: None,
            last_update: None,
            status: AppointmentStatus::Planned,
        }
    }

    // 2025-12-01 is a Monday
    const MONDAY: &str = "2025-12-01T10:00:00Z";
    const THURSDAY: &str = "2025-12-04T10:00:00Z";
    const FRIDAY: &str = "2025-12-05T10:00:00Z";
    const SUNDAY: &str = "2025-12-07T10:00:00Z";

    fn short(start: &str) -> Appointment {
        let start = at(start);
        appointment(start, start + Duration::hours(4))
    }

    #[test]
    fn appointment_reminder_rule_applies_to_everything_and_fires_30_minutes_before() {
        let a = short(MONDAY);
        assert!(AppointmentReminderRule.applies_to(&a));
        assert_eq!(
            AppointmentReminderRule.compute_trigger_times(&a),
            vec![at("2025-12-01T09:30:00Z")]
        );
        assert_eq!(AppointmentReminderRule.kind(), ReminderKind::Reminder);
    }

    #[test]
    fn long_rule_applies_from_24_whole_hours() {
        let start = at(MONDAY);
        let exactly_24h = appointment(start, start + Duration::hours(24));
        let almost_24h = appointment(start, start + Duration::hours(24) - Duration::seconds(1));
        assert!(LongAppointmentRSVPRule.applies_to(&exactly_24h));
        assert!(!LongAppointmentRSVPRule.applies_to(&almost_24h));
    }

    #[test]
    fn long_rule_has_four_fixed_triggers_then_daily_from_two_weeks_before() {
        let start = at(MONDAY);
        let a = appointment(start, start + Duration::days(3));
        let triggers = LongAppointmentRSVPRule.compute_trigger_times(&a);

        // 4 fixed + 14 daily (14 days .. 1 day before)
        assert_eq!(triggers.len(), 4 + 14);
        assert_eq!(
            triggers[..4],
            [
                start - Duration::weeks(16),
                start - Duration::weeks(8),
                start - Duration::weeks(4),
                start - Duration::weeks(2),
            ]
        );
        assert_eq!(triggers[4], start - Duration::days(14));
        assert_eq!(triggers[17], start - Duration::days(1));
        assert_eq!(LongAppointmentRSVPRule.kind(), ReminderKind::RsvpReminder);
    }

    #[test]
    fn weekday_means_monday_to_thursday() {
        for (day, expected) in [(MONDAY, true), (THURSDAY, true), (FRIDAY, false)] {
            assert_eq!(
                ShortWeekdayRSVPRule.applies_to(&short(day)),
                expected,
                "{day}"
            );
            assert_eq!(
                ShortWeekendRSVPRule.applies_to(&short(day)),
                !expected,
                "{day}"
            );
        }
        assert!(ShortWeekendRSVPRule.applies_to(&short(SUNDAY)));
    }

    #[test]
    fn short_rules_do_not_apply_to_long_appointments() {
        let start = at(MONDAY);
        let long = appointment(start, start + Duration::hours(24));
        assert!(!ShortWeekdayRSVPRule.applies_to(&long));
        assert!(!ShortWeekendRSVPRule.applies_to(&long));
    }

    #[test]
    fn weekday_rule_fires_7_4_2_and_1_days_before() {
        let a = short(MONDAY);
        let start = a.start_time;
        assert_eq!(
            ShortWeekdayRSVPRule.compute_trigger_times(&a),
            vec![
                start - Duration::days(7),
                start - Duration::days(4),
                start - Duration::days(2),
                start - Duration::days(1),
            ]
        );
    }

    #[test]
    fn weekend_rule_has_three_fixed_triggers_then_daily_from_one_week_before() {
        let a = short(SUNDAY);
        let start = a.start_time;
        let triggers = ShortWeekendRSVPRule.compute_trigger_times(&a);

        // 3 fixed + 7 daily (7 days .. 1 day before)
        assert_eq!(triggers.len(), 3 + 7);
        assert_eq!(
            triggers[..3],
            [
                start - Duration::weeks(4),
                start - Duration::weeks(2),
                start - Duration::weeks(1),
            ]
        );
        assert_eq!(triggers[3], start - Duration::days(7));
        assert_eq!(triggers[9], start - Duration::days(1));
    }

    #[test]
    fn default_rules_are_ordered_like_java() {
        let names: Vec<_> = default_rules()
            .iter()
            .map(|r| r.name().to_string())
            .collect();
        assert_eq!(
            names,
            [
                "LongAppointmentRSVPRule",
                "ShortWeekdayRSVPRule",
                "ShortWeekendRSVPRule",
                "AppointmentReminderRule"
            ]
        );
    }
}
