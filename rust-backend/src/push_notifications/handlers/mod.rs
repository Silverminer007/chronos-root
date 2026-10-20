// Handlers of push_notifications: the HTTP endpoints and the domain event handler
pub mod endpoints;
pub mod event_listener;

use crate::appointments::models::{AppointmentResponse, ParticipationStatus, UserRole};
use crate::appointments::repository::AppointmentRepository;
use crate::event_bus::Event;
use crate::push_notifications::events::{self, *};
use crate::push_notifications::services::NotificationDispatcher;
use crate::users::models::{AppointmentNotificationSetting, NotificationSetting, Settings};
use crate::users::repository::UserRepository;
use serde::de::DeserializeOwned;
use serde_json::{json, Value};
use sqlx::PgPool;
use std::sync::Arc;
use tracing::{debug, warn};

pub use endpoints::PushState;
pub use event_listener::PushNotificationEventListener;

type HandlerResult = Result<(), Box<dyn std::error::Error + Send + Sync>>;

/// Name of a user whose profile has been wiped (Java: `UserIdentity#getName`)
const DELETED_USER_NAME: &str = "Gelöschter Benutzer";
/// Sender name when the identity of a message sender is unknown
const UNKNOWN_SENDER_NAME: &str = "Unknown";

/// One row of `appointment_participation`
struct Participant {
    user_id: String,
    role: UserRole,
    status: ParticipationStatus,
}

/// Which participants get an appointment notification
struct Recipients {
    /// The acting user, who knows about the change already
    exclude: Option<String>,
    /// Only participants that have not answered yet
    pending_only: bool,
    /// The per-user setting that has to allow the notification
    setting: fn(&Settings) -> AppointmentNotificationSetting,
}

impl Recipients {
    fn by(setting: fn(&Settings) -> AppointmentNotificationSetting) -> Self {
        Self {
            exclude: None,
            pending_only: false,
            setting,
        }
    }

    fn except(mut self, user_id: &str) -> Self {
        self.exclude = Some(user_id.to_string());
        self
    }

    fn pending_only(mut self) -> Self {
        self.pending_only = true;
        self
    }
}

/// Java: `SettingsService#checkSetting`; `NONE` participants never match
fn setting_allows(setting: AppointmentNotificationSetting, role: UserRole) -> bool {
    let minimum = match setting {
        AppointmentNotificationSetting::Disabled => return false,
        AppointmentNotificationSetting::All => UserRole::Guest,
        AppointmentNotificationSetting::Attendant => UserRole::Attendant,
        AppointmentNotificationSetting::Helper => UserRole::Helper,
        AppointmentNotificationSetting::Responsible => UserRole::Responsible,
    };
    role_rank(role) >= role_rank(minimum)
}

fn role_rank(role: UserRole) -> u8 {
    match role {
        UserRole::None => 0,
        UserRole::Guest => 1,
        UserRole::Attendant => 2,
        UserRole::Helper => 3,
        UserRole::Responsible => 4,
    }
}

/// Jackson omits null fields of the appointment DTO (`@JsonInclude(NON_NULL)`)
fn without_nulls(value: &mut Value) {
    if let Value::Object(map) = value {
        map.retain(|_, v| !v.is_null());
    }
}

fn status_name(status: ParticipationStatus) -> String {
    serde_json::to_value(status)
        .ok()
        .and_then(|v| v.as_str().map(str::to_string))
        .unwrap_or_default()
}

fn parse_event<T: DeserializeOwned>(event: &Event) -> Result<T, String> {
    serde_json::from_value(event.payload.clone())
        .map_err(|e| format!("Invalid {} payload: {}", event.event_type, e))
}

/// Reacts to the domain events Java sends push notifications for (Java: `WebPushService`).
/// Recipients, settings checks, message texts and JSON shapes follow the Java observers 1:1;
/// an unknown acting user or appointment silently ends the handling, like Java's early returns.
pub struct PushEventHandler {
    pool: PgPool,
    dispatcher: Arc<NotificationDispatcher>,
}

impl PushEventHandler {
    pub fn new(pool: PgPool, dispatcher: Arc<NotificationDispatcher>) -> Self {
        Self { pool, dispatcher }
    }

    /// Dispatches an event by its type; unknown types are ignored
    pub async fn handle(&self, event: &Event) -> HandlerResult {
        debug!("Handling push event {} ({})", event.event_type, event.id);
        match event.event_type.as_str() {
            events::MESSAGE_SENT => self.on_message_sent(parse_event(event)?).await,
            events::APPOINTMENT_MOVED => self.on_appointment_moved(parse_event(event)?).await,
            events::APPOINTMENT_CANCELLED => {
                self.on_appointment_cancelled(parse_event(event)?).await
            }
            events::PARTICIPATION_ADDED => self.on_participant_added(parse_event(event)?).await,
            events::GROUP_PARTICIPATION_ADDED => {
                self.on_group_participant_added(parse_event(event)?).await
            }
            events::FRIENDSHIP_REQUEST_SENT => {
                self.on_friendship_request_sent(parse_event(event)?).await
            }
            events::FRIENDSHIP_ACCEPTED => {
                self.on_friendship_answered("FRIENDSHIP_ACCEPTED", parse_event(event)?)
                    .await
            }
            events::FRIENDSHIP_DECLINED => {
                self.on_friendship_answered("FRIENDSHIP_DECLINED", parse_event(event)?)
                    .await
            }
            events::FRIENDSHIP_REMOVED => self.on_friendship_removed(parse_event(event)?).await,
            events::GROUP_MEMBER_ADDED => self.on_group_member_added(parse_event(event)?).await,
            events::PARTICIPATION_STATUS_CHANGED => {
                self.on_status_changed(parse_event(event)?).await
            }
            events::PARTICIPATION_INVALID => {
                self.on_participation_invalid(parse_event(event)?).await
            }
            events::PARTICIPATION_STATUS_PENDING_REMINDER => {
                self.on_pending_reminder(parse_event(event)?).await
            }
            events::APPOINTMENT_REMINDER => self.on_reminder(parse_event(event)?).await,
            events::PARTICIPATION_STATUS_RECHECK_REQUESTED => {
                self.on_recheck_requested(parse_event(event)?).await
            }
            other => {
                debug!("No push notification for event type {other}");
                Ok(())
            }
        }
    }

    // ------------------------------------------------------------ event observers

    async fn on_message_sent(&self, event: MessageSent) -> HandlerResult {
        let (appointment_id, appointment_name, sender_id, body): (
            i64,
            Option<String>,
            String,
            Option<String>,
        ) = sqlx::query_as(
            "SELECT m.appointment_id, a.name, m.sender_oidcid, m.body
                 FROM message m JOIN appointment a ON a.id = m.appointment_id
                 WHERE m.id = $1",
        )
        .bind(event.message_id)
        .fetch_optional(&self.pool)
        .await?
        .ok_or_else(|| format!("Message {} not found", event.message_id))?;

        let sender_name = self
            .user_name(&sender_id)
            .await?
            .unwrap_or_else(|| UNKNOWN_SENDER_NAME.to_string());
        let payload = json!({
            "title": format!("{} schreibt zu {}", sender_name, appointment_name.unwrap_or_default()),
            "body": body.unwrap_or_default(),
        });
        self.notify_participants(
            appointment_id,
            &payload,
            Recipients::by(|s| s.appointment_message).except(&sender_id),
        )
        .await
    }

    async fn on_appointment_moved(&self, event: AppointmentMoved) -> HandlerResult {
        let Some(acting_user_name) = self.user_name(&event.acting_user_id).await? else {
            return Ok(());
        };
        let Some(appointment) = self.appointment_json(event.appointment_id).await? else {
            return Ok(());
        };
        let payload = json!({
            "type": "APPOINTMENT_MOVED",
            "appointment": appointment,
            "old_start_time": instant(event.old_start),
            "old_end_time": instant(event.old_end),
            "acting_user_id": event.acting_user_id,
            "acting_user_name": acting_user_name,
        });
        self.notify_participants(
            event.appointment_id,
            &payload,
            Recipients::by(|s| s.appointment_moved).except(&event.acting_user_id),
        )
        .await
    }

    async fn on_appointment_cancelled(&self, event: AppointmentCancelled) -> HandlerResult {
        let Some(acting_user_name) = self.user_name(&event.acting_user_id).await? else {
            return Ok(());
        };
        let Some(appointment) = self.appointment_json(event.appointment_id).await? else {
            return Ok(());
        };
        let payload = json!({
            "type": "APPOINTMENT_CANCELLED",
            "appointment": appointment,
            "who_cancelled": acting_user_name,
        });
        self.notify_participants(
            event.appointment_id,
            &payload,
            Recipients::by(|s| s.appointment_cancelled).except(&event.acting_user_id),
        )
        .await
    }

    async fn on_participant_added(&self, event: ParticipationAdded) -> HandlerResult {
        let Some(acting_user_name) = self.user_name(&event.acting_user_id).await? else {
            return Ok(());
        };
        let Some(new_participant) = self.user_name(&event.target_user_id).await? else {
            return Ok(());
        };
        let role = self
            .participants(event.appointment_id)
            .await?
            .into_iter()
            .find(|p| p.user_id == event.target_user_id)
            .map_or(UserRole::None, |p| p.role);
        let Some(appointment) = self.appointment_json(event.appointment_id).await? else {
            return Ok(());
        };
        let payload = json!({
            "type": "NEW_PARTICIPANT",
            "appointment": appointment,
            "new_participant": new_participant,
            "participant_type": "user",
            "participant_role": role.to_string(),
            "acting_user_name": acting_user_name,
        });
        self.notify_participants(
            event.appointment_id,
            &payload,
            Recipients::by(|s| s.appointment_participant_added).except(&event.acting_user_id),
        )
        .await
    }

    async fn on_group_participant_added(&self, event: GroupParticipationAdded) -> HandlerResult {
        let Some(acting_user_name) = self.user_name(&event.acting_user_id).await? else {
            return Ok(());
        };
        let Some(group_name) = self.group_name(event.group_id).await? else {
            return Ok(());
        };
        let Some(appointment) = self.appointment_json(event.appointment_id).await? else {
            return Ok(());
        };
        let payload = json!({
            "type": "NEW_PARTICIPANT",
            "appointment": appointment,
            "new_participant": group_name,
            "participant_type": "group",
            "acting_user_name": acting_user_name,
        });
        self.notify_participants(
            event.appointment_id,
            &payload,
            Recipients::by(|s| s.appointment_participant_added).except(&event.acting_user_id),
        )
        .await
    }

    async fn on_friendship_request_sent(&self, event: FriendshipRequestSent) -> HandlerResult {
        let payload = json!({
            "type": "NEW_FRIENDSHIP_REQUEST",
            "requester_id": event.requester_id,
            "requester_name": event.requester_name,
            "request_id": event.request_id,
        });
        self.send(&event.addressee_id, &payload).await;
        Ok(())
    }

    /// Accepted and declined requests differ only in the type
    async fn on_friendship_answered(&self, kind: &str, event: FriendshipAnswered) -> HandlerResult {
        let Some(addressee_name) = self.user_name(&event.addressee_id).await? else {
            return Ok(());
        };
        let payload = json!({
            "type": kind,
            "addressee_id": event.addressee_id,
            "addressee_name": addressee_name,
        });
        self.send(&event.requester_id, &payload).await;
        Ok(())
    }

    async fn on_friendship_removed(&self, event: FriendshipRemoved) -> HandlerResult {
        let Some(acting_user_name) = self.user_name(&event.acting_user_id).await? else {
            return Ok(());
        };
        let payload = json!({
            "type": "FRIENDSHIP_REMOVED",
            "acting_user_id": event.acting_user_id,
            "acting_user_name": acting_user_name,
        });
        self.send(&event.friend_id, &payload).await;
        Ok(())
    }

    async fn on_group_member_added(&self, event: GroupMemberAdded) -> HandlerResult {
        let Some(new_member_name) = self.user_name(&event.new_member_id).await? else {
            return Ok(());
        };
        let Some(group) = self.group_json(event.group_id).await? else {
            return Ok(());
        };

        let to_members = json!({
            "type": "NEW_GROUP_MEMBER",
            "group": group,
            "new_member": new_member_name,
        });
        let members: Vec<String> = sqlx::query_scalar(
            "SELECT user_oidcid FROM group_member WHERE group_id = $1 ORDER BY id",
        )
        .bind(event.group_id)
        .fetch_all(&self.pool)
        .await?;
        let users = UserRepository::new(self.pool.clone());
        for member in members.iter().filter(|m| **m != event.new_member_id) {
            let enabled = Settings::from_dto(&users.get_or_create_settings(member).await?)
                .is_some_and(|s| s.group_member_added == NotificationSetting::Enabled);
            if enabled {
                self.send(member, &to_members).await;
            }
        }

        let to_new_member = json!({
            "type": "ADDED_TO_GROUP",
            "group": group,
            "new_member": new_member_name,
        });
        self.send(&event.new_member_id, &to_new_member).await;
        Ok(())
    }

    async fn on_status_changed(&self, event: ParticipationStatusChanged) -> HandlerResult {
        let Some(user_name) = self.user_name(&event.user_id).await? else {
            return Ok(());
        };
        let Some(appointment) = self.appointment_json(event.appointment_id).await? else {
            return Ok(());
        };
        let payload = json!({
            "type": "PARTICIPATION_STATUS_CHANGED",
            "appointment": appointment,
            "new_participation_status": status_name(event.new_status),
            "user_name": user_name,
        });
        self.notify_participants(
            event.appointment_id,
            &payload,
            Recipients::by(|s| s.appointment_participation_status_changed).except(&event.user_id),
        )
        .await
    }

    async fn on_participation_invalid(&self, event: AppointmentRef) -> HandlerResult {
        let Some(appointment) = self.appointment_json(event.appointment_id).await? else {
            return Ok(());
        };
        let participants = self.participants(event.appointment_id).await?;
        let count = |status| participants.iter().filter(|p| p.status == status).count();
        let approved = count(ParticipationStatus::Approved);
        let rejected = count(ParticipationStatus::Rejected);
        let payload = json!({
            "type": "APPOINTMENT_PARTICIPATION_INVALID",
            "appointment": appointment,
            "approved_participation": approved,
            "rejected_participation": rejected,
            "pending_participation": participants.len() - approved - rejected,
        });
        self.notify_participants(
            event.appointment_id,
            &payload,
            Recipients::by(|s| s.appointment_participation_invalid),
        )
        .await
    }

    async fn on_pending_reminder(&self, event: AppointmentRef) -> HandlerResult {
        let Some(appointment) = self.appointment_json(event.appointment_id).await? else {
            return Ok(());
        };
        let payload = json!({"type": "PARTICIPATION_STATUS_PENDING", "appointment": appointment});
        self.notify_participants(
            event.appointment_id,
            &payload,
            Recipients::by(|s| s.appointment_participation_status_pending).pending_only(),
        )
        .await
    }

    async fn on_reminder(&self, event: AppointmentRef) -> HandlerResult {
        let Some(appointment) = self.appointment_json(event.appointment_id).await? else {
            return Ok(());
        };
        let payload = json!({"type": "PARTICIPATION_REMINDER", "appointment": appointment});
        self.notify_participants(
            event.appointment_id,
            &payload,
            Recipients::by(|s| s.appointment_reminder),
        )
        .await
    }

    /// Every participant is asked, regardless of settings, with their own status
    async fn on_recheck_requested(&self, event: AppointmentRef) -> HandlerResult {
        let Some(appointment) = self.appointment_json(event.appointment_id).await? else {
            return Ok(());
        };
        for participant in self.participants(event.appointment_id).await? {
            let payload = json!({
                "type": "PARTICIPATION_STATUS_RECHECK",
                "appointment": appointment,
                "participation_status": status_name(participant.status),
            });
            self.send(&participant.user_id, &payload).await;
        }
        Ok(())
    }

    // ------------------------------------------------------------ helpers

    async fn send(&self, user_id: &str, payload: &Value) {
        self.dispatcher.send(user_id, &payload.to_string()).await;
    }

    async fn notify_participants(
        &self,
        appointment_id: i64,
        payload: &Value,
        recipients: Recipients,
    ) -> HandlerResult {
        let users = UserRepository::new(self.pool.clone());
        for participant in self.participants(appointment_id).await? {
            if recipients.exclude.as_deref() == Some(participant.user_id.as_str())
                || (recipients.pending_only && participant.status != ParticipationStatus::Pending)
            {
                continue;
            }
            let allowed =
                Settings::from_dto(&users.get_or_create_settings(&participant.user_id).await?)
                    .is_some_and(|s| setting_allows((recipients.setting)(&s), participant.role));
            if allowed {
                self.send(&participant.user_id, payload).await;
            }
        }
        Ok(())
    }

    async fn participants(
        &self,
        appointment_id: i64,
    ) -> Result<Vec<Participant>, Box<dyn std::error::Error + Send + Sync>> {
        let rows: Vec<(String, String, String)> = sqlx::query_as(
            "SELECT user_oidcid, role, status FROM appointment_participation
             WHERE appointment_id = $1 ORDER BY id",
        )
        .bind(appointment_id)
        .fetch_all(&self.pool)
        .await?;
        let mut participants = Vec::with_capacity(rows.len());
        for (user_id, role, status) in rows {
            let Ok(role) = role.parse::<UserRole>() else {
                warn!("Skipping participation of {user_id} with unknown role {role}");
                continue;
            };
            let Ok(status) = serde_json::from_value(Value::String(status.clone())) else {
                warn!("Skipping participation of {user_id} with unknown status {status}");
                continue;
            };
            participants.push(Participant {
                user_id,
                role,
                status,
            });
        }
        Ok(participants)
    }

    /// The appointment as the REST DTO, embedded as a JSON *string* like Java does
    async fn appointment_json(
        &self,
        appointment_id: i64,
    ) -> Result<Option<String>, Box<dyn std::error::Error + Send + Sync>> {
        let appointment = AppointmentRepository::new(self.pool.clone())
            .find_by_id_any_status(appointment_id)
            .await?;
        Ok(appointment.map(|a| {
            let mut value = serde_json::to_value(AppointmentResponse::from(a)).unwrap_or_default();
            without_nulls(&mut value);
            value.to_string()
        }))
    }

    async fn group_name(
        &self,
        group_id: i64,
    ) -> Result<Option<String>, Box<dyn std::error::Error + Send + Sync>> {
        let name: Option<Option<String>> =
            sqlx::query_scalar("SELECT groupname FROM groups WHERE id = $1")
                .bind(group_id)
                .fetch_optional(&self.pool)
                .await?;
        Ok(name.map(Option::unwrap_or_default))
    }

    /// The group as the REST DTO (`id`, `name`, `members`), embedded as a JSON string
    async fn group_json(
        &self,
        group_id: i64,
    ) -> Result<Option<String>, Box<dyn std::error::Error + Send + Sync>> {
        let Some(name) = self.group_name(group_id).await? else {
            return Ok(None);
        };
        let members: Vec<(String, Option<String>, Option<String>)> = sqlx::query_as(
            "SELECT gm.user_oidcid, up.first_name, up.last_name
             FROM group_member gm LEFT JOIN user_profiles up ON up.oidc_id = gm.user_oidcid
             WHERE gm.group_id = $1 ORDER BY gm.id",
        )
        .bind(group_id)
        .fetch_all(&self.pool)
        .await?;
        let members: Vec<Value> = members
            .into_iter()
            .map(|(id, first_name, last_name)| {
                json!({"id": id, "first_name": first_name, "last_name": last_name})
            })
            .collect();
        Ok(Some(
            json!({"id": group_id, "name": name, "members": members}).to_string(),
        ))
    }

    /// Display name of a user; `None` for an unknown user (Java: `UserIdentity#getName`)
    async fn user_name(
        &self,
        user_id: &str,
    ) -> Result<Option<String>, Box<dyn std::error::Error + Send + Sync>> {
        let profile: Option<(Option<String>, Option<String>, Option<String>)> = sqlx::query_as(
            "SELECT first_name, last_name, email FROM user_profiles WHERE oidc_id = $1",
        )
        .bind(user_id)
        .fetch_optional(&self.pool)
        .await?;
        Ok(profile.map(|(first_name, last_name, email)| {
            if first_name.is_none() && last_name.is_none() && email.is_none() {
                return DELETED_USER_NAME.to_string();
            }
            format!(
                "{} {}",
                first_name.unwrap_or_default(),
                last_name.unwrap_or_default()
            )
            .trim()
            .to_string()
        }))
    }
}

/// Java: `Instant#toString`
fn instant(time: chrono::DateTime<chrono::Utc>) -> String {
    time.to_rfc3339_opts(chrono::SecondsFormat::AutoSi, true)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_setting_allows_by_role() {
        use AppointmentNotificationSetting as S;
        assert!(setting_allows(S::All, UserRole::Guest));
        assert!(!setting_allows(S::All, UserRole::None));
        assert!(setting_allows(S::Attendant, UserRole::Helper));
        assert!(!setting_allows(S::Attendant, UserRole::Guest));
        assert!(setting_allows(S::Responsible, UserRole::Responsible));
        assert!(!setting_allows(S::Responsible, UserRole::Helper));
        assert!(!setting_allows(S::Disabled, UserRole::Responsible));
    }

    #[test]
    fn test_instant_matches_java_to_string() {
        let time = chrono::DateTime::parse_from_rfc3339("2026-01-01T10:00:00Z")
            .unwrap()
            .with_timezone(&chrono::Utc);
        assert_eq!(instant(time), "2026-01-01T10:00:00Z");
    }

    #[test]
    fn test_without_nulls_drops_null_fields() {
        let mut value = json!({"id": 1, "description": null});
        without_nulls(&mut value);
        assert_eq!(value, json!({"id": 1}));
    }
}
