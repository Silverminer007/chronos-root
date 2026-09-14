use chrono::Utc;
use std::sync::Arc;
use tokio::sync::Mutex;
use uuid::Uuid;
use crate::appointments::repository::AppointmentRepository;
use crate::database::repository::Repository;
use crate::event_bus::EventBus;
use crate::reminders::rules::{AppointmentReminderRule, LongAppointmentRSVPRule, ShortWeekdayRSVPRule, ShortWeekendRSVPRule};
use crate::reminders::services::ReminderRuleEngine;

pub struct ReminderScheduler {
    event_bus: Arc<dyn EventBus>,
    appointment_repo: Arc<AppointmentRepository>,
    engine: Arc<ReminderRuleEngine>,
    is_leader: Arc<Mutex<bool>>,
    node_id: String,
}

impl ReminderScheduler {
    pub fn new(event_bus: Arc<dyn EventBus>, appointment_repo: Arc<AppointmentRepository>) -> Self {
        let rules = vec![
            Box::new(AppointmentReminderRule) as Box<dyn crate::reminders::rules::ReminderRule>,
            Box::new(LongAppointmentRSVPRule),
            Box::new(ShortWeekdayRSVPRule),
            Box::new(ShortWeekendRSVPRule),
        ];
        let engine = Arc::new(ReminderRuleEngine::new(event_bus.clone(), rules));

        Self {
            event_bus,
            appointment_repo,
            engine,
            is_leader: Arc::new(Mutex::new(false)),
            node_id: Uuid::new_v4().to_string(),
        }
    }

    pub async fn run(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        self.attempt_leader_election().await?;
        let is_leader = *self.is_leader.lock().await;
        if !is_leader {
            tracing::debug!("Not leader, skipping reminder evaluation");
            return Ok(());
        }

        let appointments = self.appointment_repo.find_all().await
            .map_err(|e| Box::new(e) as Box<dyn std::error::Error + Send + Sync>)?;

        for appointment in appointments {
            if appointment.end_time < Utc::now() {
                continue;
            }
            let reminder_events = self.engine.evaluate_appointment(&appointment).await;
            for event in reminder_events {
                self.engine.fire_reminder_event(event).await?;
            }
        }

        Ok(())
    }

    async fn attempt_leader_election(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut is_leader = self.is_leader.lock().await;
        if !*is_leader {
            *is_leader = true;
            tracing::info!("Node {} acquired leadership", self.node_id);
        }
        Ok(())
    }

    pub fn node_id(&self) -> &str {
        &self.node_id
    }

    pub async fn is_leader(&self) -> bool {
        *self.is_leader.lock().await
    }
}
