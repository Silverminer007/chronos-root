//! Users are identified by their OIDC subject; their profile is cached in `user_profiles`
//! (ADR-2026-10-12-014). There is no `users` table.

use serde::{Deserialize, Serialize};
use sqlx::FromRow;

pub use crate::appointments::models::UserProfile;

/// Java: `PrincipalDto`. Nulls are serialised as `null`, like Jackson does in Java.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct PrincipalDto {
    #[serde(default)]
    pub id: Option<String>,
    #[serde(default)]
    pub first_name: Option<String>,
    #[serde(default)]
    pub last_name: Option<String>,
    #[serde(default)]
    pub email: Option<String>,
}

/// Java: `UpdatedUserDto`
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct UpdatedUserDto {
    pub user: PrincipalDto,
    #[serde(rename = "verifyEmailUrl")]
    pub verify_email_url: String,
}

/// Java: `LinkedAccountDto`
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct LinkedAccountDto {
    pub provider: String,
}

/// Java: `PasskeyDto`
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct PasskeyDto {
    pub id: String,
    pub name: Option<String>,
    pub created_at: Option<String>,
    pub last_used_at: Option<String>,
}

/// Body of the `link`, `passkey` and `change-password` endpoints
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct UrlResponse {
    pub url: String,
}

/// Java: `AppointmentNotificationSetting`
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AppointmentNotificationSetting {
    Disabled,
    All,
    Attendant,
    Helper,
    Responsible,
}

impl AppointmentNotificationSetting {
    /// Java's `SettingsMapper`: trimmed, case-insensitive; blank or unknown values map to null
    pub fn parse(value: &str) -> Option<Self> {
        match value.trim().to_uppercase().as_str() {
            "DISABLED" => Some(Self::Disabled),
            "ALL" => Some(Self::All),
            "ATTENDANT" => Some(Self::Attendant),
            "HELPER" => Some(Self::Helper),
            "RESPONSIBLE" => Some(Self::Responsible),
            _ => None,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Self::Disabled => "DISABLED",
            Self::All => "ALL",
            Self::Attendant => "ATTENDANT",
            Self::Helper => "HELPER",
            Self::Responsible => "RESPONSIBLE",
        }
    }
}

/// Java: `NotificationSetting`
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NotificationSetting {
    Disabled,
    Enabled,
}

impl NotificationSetting {
    pub fn parse(value: &str) -> Option<Self> {
        match value.trim().to_uppercase().as_str() {
            "DISABLED" => Some(Self::Disabled),
            "ENABLED" => Some(Self::Enabled),
            _ => None,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Self::Disabled => "DISABLED",
            Self::Enabled => "ENABLED",
        }
    }
}

/// Java: `SettingsDto`. Request fields are optional so that a missing field is a (Java-parity)
/// validation failure instead of a deserialisation error.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize, FromRow)]
pub struct SettingsDto {
    #[serde(default)]
    pub appointment_moved: Option<String>,
    #[serde(default)]
    pub appointment_message: Option<String>,
    #[serde(default)]
    pub appointment_cancelled: Option<String>,
    #[serde(default)]
    pub appointment_participant_added: Option<String>,
    #[serde(default)]
    pub appointment_participation_status_changed: Option<String>,
    #[serde(default)]
    pub appointment_participation_invalid: Option<String>,
    #[serde(default)]
    pub appointment_participation_status_pending: Option<String>,
    #[serde(default)]
    pub appointment_reminder: Option<String>,
    #[serde(default)]
    pub group_member_added: Option<String>,
}

/// A complete, validated settings record (all columns are NOT NULL)
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Settings {
    pub appointment_moved: AppointmentNotificationSetting,
    pub appointment_message: AppointmentNotificationSetting,
    pub appointment_cancelled: AppointmentNotificationSetting,
    pub appointment_participant_added: AppointmentNotificationSetting,
    pub appointment_participation_status_changed: AppointmentNotificationSetting,
    pub appointment_participation_invalid: AppointmentNotificationSetting,
    pub appointment_participation_status_pending: AppointmentNotificationSetting,
    pub appointment_reminder: AppointmentNotificationSetting,
    pub group_member_added: NotificationSetting,
}

impl Settings {
    /// Validates a request: every field must be present and a known value. Java maps invalid values to
    /// null and then fails on the NOT NULL columns (500, nothing persisted).
    pub fn from_dto(dto: &SettingsDto) -> Option<Self> {
        let appointment = |value: &Option<String>| {
            value
                .as_deref()
                .and_then(AppointmentNotificationSetting::parse)
        };
        Some(Self {
            appointment_moved: appointment(&dto.appointment_moved)?,
            appointment_message: appointment(&dto.appointment_message)?,
            appointment_cancelled: appointment(&dto.appointment_cancelled)?,
            appointment_participant_added: appointment(&dto.appointment_participant_added)?,
            appointment_participation_status_changed: appointment(
                &dto.appointment_participation_status_changed,
            )?,
            appointment_participation_invalid: appointment(&dto.appointment_participation_invalid)?,
            appointment_participation_status_pending: appointment(
                &dto.appointment_participation_status_pending,
            )?,
            appointment_reminder: appointment(&dto.appointment_reminder)?,
            group_member_added: dto
                .group_member_added
                .as_deref()
                .and_then(NotificationSetting::parse)?,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::Utc;

    #[test]
    fn test_user_profile_is_keyed_by_oidc_subject() {
        let profile = UserProfile {
            oidc_id: "keycloak-sub-1".to_string(),
            first_name: Some("John".to_string()),
            last_name: Some("Doe".to_string()),
            email: Some("user@example.com".to_string()),
            profile_picture_url: None,
            updated_at: Utc::now(),
        };

        assert_eq!(profile.oidc_id, "keycloak-sub-1");
        assert!(profile.first_name.is_some());
    }

    #[test]
    fn test_user_profile_without_name() {
        let profile = UserProfile {
            oidc_id: "keycloak-sub-2".to_string(),
            first_name: None,
            last_name: None,
            email: None,
            profile_picture_url: None,
            updated_at: Utc::now(),
        };

        assert!(profile.first_name.is_none());
        assert!(profile.last_name.is_none());
    }

    #[test]
    fn test_principal_dto_serialises_nulls() {
        let dto = PrincipalDto {
            id: Some("u1".into()),
            ..Default::default()
        };
        assert_eq!(
            serde_json::to_value(dto).unwrap(),
            serde_json::json!({"id": "u1", "first_name": null, "last_name": null, "email": null})
        );
    }

    #[test]
    fn test_setting_parse_is_trimmed_and_case_insensitive() {
        assert_eq!(
            AppointmentNotificationSetting::parse(" responsible "),
            Some(AppointmentNotificationSetting::Responsible)
        );
        assert_eq!(
            NotificationSetting::parse("enabled"),
            Some(NotificationSetting::Enabled)
        );
    }

    #[test]
    fn test_setting_parse_rejects_blank_and_unknown_values() {
        assert_eq!(AppointmentNotificationSetting::parse(""), None);
        assert_eq!(AppointmentNotificationSetting::parse("  "), None);
        assert_eq!(AppointmentNotificationSetting::parse("SOMETIMES"), None);
        // ENABLED belongs to NotificationSetting only
        assert_eq!(AppointmentNotificationSetting::parse("ENABLED"), None);
        assert_eq!(NotificationSetting::parse("ALL"), None);
    }

    fn full_dto() -> SettingsDto {
        SettingsDto {
            appointment_moved: Some("ALL".into()),
            appointment_message: Some("attendant".into()),
            appointment_cancelled: Some("ALL".into()),
            appointment_participant_added: Some("RESPONSIBLE".into()),
            appointment_participation_status_changed: Some("HELPER".into()),
            appointment_participation_invalid: Some("ATTENDANT".into()),
            appointment_participation_status_pending: Some("DISABLED".into()),
            appointment_reminder: Some("ALL".into()),
            group_member_added: Some("ENABLED".into()),
        }
    }

    #[test]
    fn test_settings_from_dto_accepts_a_complete_dto() {
        let settings = Settings::from_dto(&full_dto()).unwrap();
        assert_eq!(
            settings.appointment_message,
            AppointmentNotificationSetting::Attendant
        );
        assert_eq!(settings.group_member_added, NotificationSetting::Enabled);
    }

    #[test]
    fn test_settings_from_dto_rejects_missing_or_invalid_fields() {
        let mut missing = full_dto();
        missing.appointment_reminder = None;
        assert!(Settings::from_dto(&missing).is_none());

        let mut invalid = full_dto();
        invalid.group_member_added = Some("ALL".into());
        assert!(Settings::from_dto(&invalid).is_none());
    }
}
