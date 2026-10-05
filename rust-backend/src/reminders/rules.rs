use crate::appointments::models::Appointment;
use crate::reminders::models::ReminderTriggerTimes;
use async_trait::async_trait;
use chrono::{Datelike, Duration, Utc};

#[async_trait]
pub trait ReminderRule: Send + Sync {
    async fn evaluate(&self, appointment: &Appointment) -> ReminderTriggerTimes;
    fn name(&self) -> &str;
}

pub struct AppointmentReminderRule;

#[async_trait]
impl ReminderRule for AppointmentReminderRule {
    async fn evaluate(&self, appointment: &Appointment) -> ReminderTriggerTimes {
        let trigger_time = appointment.start_time - Duration::minutes(30);
        ReminderTriggerTimes {
            appointment_id: appointment.id,
            trigger_times: vec![trigger_time],
        }
    }
    fn name(&self) -> &str {
        "AppointmentReminderRule"
    }
}

pub struct LongAppointmentRSVPRule;

#[async_trait]
impl ReminderRule for LongAppointmentRSVPRule {
    async fn evaluate(&self, appointment: &Appointment) -> ReminderTriggerTimes {
        let duration = appointment.end_time - appointment.start_time;
        if duration < Duration::hours(24) {
            return ReminderTriggerTimes {
                appointment_id: appointment.id,
                trigger_times: vec![],
            };
        }

        let mut trigger_times = Vec::new();
        let start = appointment.start_time;
        trigger_times.push(start - Duration::weeks(16));
        trigger_times.push(start - Duration::weeks(8));
        trigger_times.push(start - Duration::weeks(4));
        trigger_times.push(start - Duration::weeks(2));

        let mut current = start - Duration::days(7);
        while current < start {
            trigger_times.push(current);
            current += Duration::days(1);
        }

        ReminderTriggerTimes {
            appointment_id: appointment.id,
            trigger_times,
        }
    }
    fn name(&self) -> &str {
        "LongAppointmentRSVPRule"
    }
}

pub struct ShortWeekdayRSVPRule;

#[async_trait]
impl ReminderRule for ShortWeekdayRSVPRule {
    async fn evaluate(&self, appointment: &Appointment) -> ReminderTriggerTimes {
        let duration = appointment.end_time - appointment.start_time;
        if duration >= Duration::hours(24)
            || appointment.start_time.weekday().number_from_monday() > 5
        {
            return ReminderTriggerTimes {
                appointment_id: appointment.id,
                trigger_times: vec![],
            };
        }

        let mut trigger_times = Vec::new();
        let start = appointment.start_time;
        trigger_times.push(start - Duration::days(7));
        trigger_times.push(start - Duration::days(4));
        trigger_times.push(start - Duration::days(2));
        trigger_times.push(start - Duration::days(1));

        ReminderTriggerTimes {
            appointment_id: appointment.id,
            trigger_times,
        }
    }
    fn name(&self) -> &str {
        "ShortWeekdayRSVPRule"
    }
}

pub struct ShortWeekendRSVPRule;

#[async_trait]
impl ReminderRule for ShortWeekendRSVPRule {
    async fn evaluate(&self, appointment: &Appointment) -> ReminderTriggerTimes {
        let duration = appointment.end_time - appointment.start_time;
        if duration >= Duration::hours(24)
            || appointment.start_time.weekday().number_from_monday() <= 5
        {
            return ReminderTriggerTimes {
                appointment_id: appointment.id,
                trigger_times: vec![],
            };
        }

        let mut trigger_times = Vec::new();
        let start = appointment.start_time;
        trigger_times.push(start - Duration::weeks(4));
        trigger_times.push(start - Duration::weeks(2));
        trigger_times.push(start - Duration::weeks(1));

        let mut current = Utc::now();
        while current < start {
            trigger_times.push(current);
            current += Duration::days(1);
        }

        ReminderTriggerTimes {
            appointment_id: appointment.id,
            trigger_times,
        }
    }
    fn name(&self) -> &str {
        "ShortWeekendRSVPRule"
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    fn create_test_appointment(
        start: chrono::DateTime<Utc>,
        end: chrono::DateTime<Utc>,
    ) -> Appointment {
        use crate::appointments::models::AppointmentStatus;
        Appointment {
            id: Uuid::new_v4(),
            title: "Test Appointment".to_string(),
            description: None,
            location: None,
            start_time: start,
            end_time: end,
            creator_id: Uuid::new_v4(),
            minimal_attendees: None,
            created_at: Utc::now(),
            updated_at: Utc::now(),
            status: AppointmentStatus::Planned,
        }
    }

    #[tokio::test]
    async fn test_appointment_reminder_rule() {
        let rule = AppointmentReminderRule;
        let start = Utc::now() + Duration::days(1);
        let end = start + Duration::hours(1);
        let appointment = create_test_appointment(start, end);

        let result = rule.evaluate(&appointment).await;
        assert_eq!(result.appointment_id, appointment.id);
        assert_eq!(result.trigger_times.len(), 1);
        assert_eq!(result.trigger_times[0], start - Duration::minutes(30));
    }

    #[tokio::test]
    async fn test_appointment_reminder_rule_name() {
        let rule = AppointmentReminderRule;
        assert_eq!(rule.name(), "AppointmentReminderRule");
    }

    #[tokio::test]
    async fn test_long_appointment_rsvp_rule_short_duration() {
        let rule = LongAppointmentRSVPRule;
        let start = Utc::now() + Duration::days(1);
        let end = start + Duration::hours(12);
        let appointment = create_test_appointment(start, end);

        let result = rule.evaluate(&appointment).await;
        assert_eq!(result.trigger_times.len(), 0);
    }

    #[tokio::test]
    async fn test_long_appointment_rsvp_rule_long_duration() {
        let rule = LongAppointmentRSVPRule;
        let start = Utc::now() + Duration::days(100);
        let end = start + Duration::days(2);
        let appointment = create_test_appointment(start, end);

        let result = rule.evaluate(&appointment).await;
        assert!(result.trigger_times.len() > 0);
        assert!(result.trigger_times.len() <= 30); // Should have multiple trigger times
    }

    #[tokio::test]
    async fn test_long_appointment_rsvp_rule_name() {
        let rule = LongAppointmentRSVPRule;
        assert_eq!(rule.name(), "LongAppointmentRSVPRule");
    }

    #[tokio::test]
    async fn test_short_weekday_rsvp_rule_long_duration() {
        let rule = ShortWeekdayRSVPRule;
        let start = chrono::NaiveDate::from_ymd_opt(2025, 12, 1)
            .and_then(|d| d.and_hms_opt(10, 0, 0))
            .map(|dt| dt.and_utc())
            .unwrap();
        let end = start + Duration::hours(24);
        let appointment = create_test_appointment(start, end);

        let result = rule.evaluate(&appointment).await;
        assert_eq!(result.trigger_times.len(), 0);
    }

    #[tokio::test]
    async fn test_short_weekday_rsvp_rule_weekend() {
        let rule = ShortWeekdayRSVPRule;
        let start = chrono::NaiveDate::from_ymd_opt(2025, 12, 6)
            .and_then(|d| d.and_hms_opt(10, 0, 0))
            .map(|dt| dt.and_utc())
            .unwrap();
        let end = start + Duration::hours(4);
        let appointment = create_test_appointment(start, end);

        let result = rule.evaluate(&appointment).await;
        assert_eq!(result.trigger_times.len(), 0);
    }

    #[tokio::test]
    async fn test_short_weekday_rsvp_rule_weekday_short() {
        let rule = ShortWeekdayRSVPRule;
        let start = chrono::NaiveDate::from_ymd_opt(2025, 12, 1)
            .and_then(|d| d.and_hms_opt(10, 0, 0))
            .map(|dt| dt.and_utc())
            .unwrap();
        let end = start + Duration::hours(4);
        let appointment = create_test_appointment(start, end);

        let result = rule.evaluate(&appointment).await;
        assert_eq!(result.trigger_times.len(), 4);
    }

    #[tokio::test]
    async fn test_short_weekday_rsvp_rule_name() {
        let rule = ShortWeekdayRSVPRule;
        assert_eq!(rule.name(), "ShortWeekdayRSVPRule");
    }

    #[tokio::test]
    async fn test_short_weekend_rsvp_rule_long_duration() {
        let rule = ShortWeekendRSVPRule;
        let start = chrono::NaiveDate::from_ymd_opt(2025, 12, 6)
            .and_then(|d| d.and_hms_opt(10, 0, 0))
            .map(|dt| dt.and_utc())
            .unwrap();
        let end = start + Duration::hours(24);
        let appointment = create_test_appointment(start, end);

        let result = rule.evaluate(&appointment).await;
        assert_eq!(result.trigger_times.len(), 0);
    }

    #[tokio::test]
    async fn test_short_weekend_rsvp_rule_weekday() {
        let rule = ShortWeekendRSVPRule;
        let start = chrono::NaiveDate::from_ymd_opt(2025, 12, 1)
            .and_then(|d| d.and_hms_opt(10, 0, 0))
            .map(|dt| dt.and_utc())
            .unwrap();
        let end = start + Duration::hours(4);
        let appointment = create_test_appointment(start, end);

        let result = rule.evaluate(&appointment).await;
        assert_eq!(result.trigger_times.len(), 0);
    }

    #[tokio::test]
    async fn test_short_weekend_rsvp_rule_name() {
        let rule = ShortWeekendRSVPRule;
        assert_eq!(rule.name(), "ShortWeekendRSVPRule");
    }
}
