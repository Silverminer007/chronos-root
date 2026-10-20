use sqlx::PgPool;

use super::models::{Settings, SettingsDto};
use crate::security::PrincipalContext;

/// Settings defaults of Flyway V1.2.0 / Java `SettingsService#getDefaultSetting`. They live as column
/// defaults in the schema, so a row inserted with only `user_oidcid` carries them.
const SETTINGS_COLUMNS: &str = "appointment_moved, appointment_message, appointment_cancelled, \
     appointment_participant_added, appointment_participation_status_changed, \
     appointment_participation_invalid, appointment_participation_status_pending, \
     appointment_reminder, group_member_added";

/// Persistence of settings and of the cached identity (`user_profiles`)
#[derive(Clone)]
pub struct UserRepository {
    pool: PgPool,
}

impl UserRepository {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Java: `SettingsService#getOrCreateSettings`. The unique index on `user_oidcid` makes the
    /// create race-free.
    pub async fn get_or_create_settings(&self, user_id: &str) -> Result<SettingsDto, sqlx::Error> {
        sqlx::query(
            "INSERT INTO settings (user_oidcid) VALUES ($1) ON CONFLICT (user_oidcid) DO NOTHING",
        )
        .bind(user_id)
        .execute(&self.pool)
        .await?;
        sqlx::query_as::<_, SettingsDto>(&format!(
            "SELECT {} FROM settings WHERE user_oidcid = $1",
            SETTINGS_COLUMNS
        ))
        .bind(user_id)
        .fetch_one(&self.pool)
        .await
    }

    /// Java: `SettingsService#updateSettings` (creates the row when missing)
    pub async fn save_settings(
        &self,
        user_id: &str,
        settings: &Settings,
    ) -> Result<(), sqlx::Error> {
        sqlx::query(&format!(
            "INSERT INTO settings (user_oidcid, {cols}) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10) \
             ON CONFLICT (user_oidcid) DO UPDATE SET \
             appointment_moved = EXCLUDED.appointment_moved, \
             appointment_message = EXCLUDED.appointment_message, \
             appointment_cancelled = EXCLUDED.appointment_cancelled, \
             appointment_participant_added = EXCLUDED.appointment_participant_added, \
             appointment_participation_status_changed = EXCLUDED.appointment_participation_status_changed, \
             appointment_participation_invalid = EXCLUDED.appointment_participation_invalid, \
             appointment_participation_status_pending = EXCLUDED.appointment_participation_status_pending, \
             appointment_reminder = EXCLUDED.appointment_reminder, \
             group_member_added = EXCLUDED.group_member_added",
            cols = SETTINGS_COLUMNS
        ))
        .bind(user_id)
        .bind(settings.appointment_moved.as_str())
        .bind(settings.appointment_message.as_str())
        .bind(settings.appointment_cancelled.as_str())
        .bind(settings.appointment_participant_added.as_str())
        .bind(settings.appointment_participation_status_changed.as_str())
        .bind(settings.appointment_participation_invalid.as_str())
        .bind(settings.appointment_participation_status_pending.as_str())
        .bind(settings.appointment_reminder.as_str())
        .bind(settings.group_member_added.as_str())
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    /// Java: `LocalDbIdentityAdapter#upsert` – the token claims are the source of the cached profile
    pub async fn upsert_profile(&self, principal: &PrincipalContext) -> Result<(), sqlx::Error> {
        sqlx::query(
            "INSERT INTO user_profiles (oidc_id, first_name, last_name, email, profile_picture_url, updated_at) \
             VALUES ($1, $2, $3, $4, $5, NOW()) \
             ON CONFLICT (oidc_id) DO UPDATE SET \
             first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name, \
             email = EXCLUDED.email, profile_picture_url = EXCLUDED.profile_picture_url, \
             updated_at = EXCLUDED.updated_at",
        )
        .bind(principal.user_id())
        .bind(&principal.first_name)
        .bind(&principal.last_name)
        .bind(&principal.email)
        .bind(&principal.profile_picture_url)
        .execute(&self.pool)
        .await?;
        Ok(())
    }
}
