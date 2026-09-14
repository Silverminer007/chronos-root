use async_trait::async_trait;
use chrono::{Duration, Utc};
use crate::appointments::models::Appointment;
use crate::reminders::models::ReminderTriggerTimes;

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
    fn name(&self) -> &str { "AppointmentReminderRule" }
}

pub struct LongAppointmentRSVPRule;

#[async_trait]
impl ReminderRule for LongAppointmentRSVPRule {
    async fn evaluate(&self, appointment: &Appointment) -> ReminderTriggerTimes {
        let duration = appointment.end_time - appointment.start_time;
        if duration < Duration::hours(24) {
            return ReminderTriggerTimes { appointment_id: appointment.id, trigger_times: vec![] };
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
            current = current + Duration::days(1);
        }

        ReminderTriggerTimes { appointment_id: appointment.id, trigger_times }
    }
    fn name(&self) -> &str { "LongAppointmentRSVPRule" }
}

pub struct ShortWeekdayRSVPRule;

#[async_trait]
impl ReminderRule for ShortWeekdayRSVPRule {
    async fn evaluate(&self, appointment: &Appointment) -> ReminderTriggerTimes {
        let duration = appointment.end_time - appointment.start_time;
        if duration >= Duration::hours(24) || appointment.start_time.weekday().number_from_monday() > 5 {
            return ReminderTriggerTimes { appointment_id: appointment.id, trigger_times: vec![] };
        }

        let mut trigger_times = Vec::new();
        let start = appointment.start_time;
        trigger_times.push(start - Duration::days(7));
        trigger_times.push(start - Duration::days(4));
        trigger_times.push(start - Duration::days(2));
        trigger_times.push(start - Duration::days(1));

        ReminderTriggerTimes { appointment_id: appointment.id, trigger_times }
    }
    fn name(&self) -> &str { "ShortWeekdayRSVPRule" }
}

pub struct ShortWeekendRSVPRule;

#[async_trait]
impl ReminderRule for ShortWeekendRSVPRule {
    async fn evaluate(&self, appointment: &Appointment) -> ReminderTriggerTimes {
        let duration = appointment.end_time - appointment.start_time;
        if duration >= Duration::hours(24) || appointment.start_time.weekday().number_from_monday() <= 5 {
            return ReminderTriggerTimes { appointment_id: appointment.id, trigger_times: vec![] };
        }

        let mut trigger_times = Vec::new();
        let start = appointment.start_time;
        trigger_times.push(start - Duration::weeks(4));
        trigger_times.push(start - Duration::weeks(2));
        trigger_times.push(start - Duration::weeks(1));

        let mut current = Utc::now();
        while current < start {
            trigger_times.push(current);
            current = current + Duration::days(1);
        }

        ReminderTriggerTimes { appointment_id: appointment.id, trigger_times }
    }
    fn name(&self) -> &str { "ShortWeekendRSVPRule" }
}
