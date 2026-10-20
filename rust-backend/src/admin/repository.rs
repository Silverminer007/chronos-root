//! Queries of the admin endpoints that no other feature owns: statistics and the push log.

use chrono::{DateTime, Utc};
use sqlx::PgPool;

use crate::admin::models::{
    AppointmentStatistics, FriendshipStatistics, GroupStatistics, ParticipationStatistics,
    PushNotificationLogDto, Total,
};
use crate::appointments::models::AppointmentStatus;
use crate::groups::models::{format_instant, FriendshipStatus};

/// Filters of `GET /push/log`; `None` means unfiltered
#[derive(Debug, Default)]
pub struct PushLogFilter {
    pub user_id: Option<String>,
    pub from: Option<DateTime<Utc>>,
    pub to: Option<DateTime<Utc>>,
    pub success: Option<bool>,
    pub notification_type: Option<String>,
}

#[derive(sqlx::FromRow)]
struct PushLogRow {
    id: i64,
    user_oidcid: String,
    notification_type: Option<String>,
    payload: String,
    endpoint: String,
    http_status_code: Option<i32>,
    success: bool,
    error_message: Option<String>,
    created_at: Option<DateTime<Utc>>,
}

impl From<PushLogRow> for PushNotificationLogDto {
    fn from(row: PushLogRow) -> Self {
        Self {
            id: row.id,
            user_id: row.user_oidcid,
            notification_type: row.notification_type,
            payload: row.payload,
            endpoint: row.endpoint,
            http_status_code: row.http_status_code,
            success: row.success,
            error_message: row.error_message,
            created_at: row.created_at.map(format_instant),
        }
    }
}

/// Newest entries first, like Java's `Sort.descending("createdAt")` (id breaks ties)
pub async fn find_push_log(
    pool: &PgPool,
    filter: &PushLogFilter,
    offset: i64,
    limit: i64,
) -> Result<Vec<PushNotificationLogDto>, sqlx::Error> {
    let rows = sqlx::query_as::<_, PushLogRow>(
        "SELECT id, user_oidcid, notification_type, payload, endpoint, http_status_code,
                success, error_message, created_at
         FROM push_notification_log
         WHERE ($1::text IS NULL OR user_oidcid = $1)
           AND ($2::timestamptz IS NULL OR created_at >= $2)
           AND ($3::timestamptz IS NULL OR created_at <= $3)
           AND ($4::boolean IS NULL OR success = $4)
           AND ($5::text IS NULL OR notification_type = $5)
         ORDER BY created_at DESC, id DESC
         LIMIT $6 OFFSET $7",
    )
    .bind(&filter.user_id)
    .bind(filter.from)
    .bind(filter.to)
    .bind(filter.success)
    .bind(&filter.notification_type)
    .bind(limit)
    .bind(offset)
    .fetch_all(pool)
    .await?;
    Ok(rows.into_iter().map(Into::into).collect())
}

async fn count(pool: &PgPool, table: &str, condition: &str) -> Result<i64, sqlx::Error> {
    sqlx::query_scalar::<_, i64>(&format!("SELECT COUNT(*) FROM {table} WHERE {condition}"))
        .fetch_one(pool)
        .await
}

async fn count_all(pool: &PgPool, table: &str) -> Result<i64, sqlx::Error> {
    count(pool, table, "TRUE").await
}

async fn count_appointments(pool: &PgPool, status: AppointmentStatus) -> Result<i64, sqlx::Error> {
    count(
        pool,
        "appointment",
        &format!("status = {}", status.ordinal()),
    )
    .await
}

async fn count_by_status(pool: &PgPool, table: &str, status: &str) -> Result<i64, sqlx::Error> {
    count(pool, table, &format!("status = '{status}'")).await
}

pub async fn appointment_statistics(pool: &PgPool) -> Result<AppointmentStatistics, sqlx::Error> {
    Ok(AppointmentStatistics {
        total: count_all(pool, "appointment").await?,
        planned: count_appointments(pool, AppointmentStatus::Planned).await?,
        cancelled: count_appointments(pool, AppointmentStatus::Cancelled).await?,
        deleted: count_appointments(pool, AppointmentStatus::Deleted).await?,
        not_enough_attendees: count_appointments(pool, AppointmentStatus::NotEnoughAttendees)
            .await?,
    })
}

pub async fn participation_statistics(
    pool: &PgPool,
) -> Result<ParticipationStatistics, sqlx::Error> {
    const TABLE: &str = "appointment_participation";
    Ok(ParticipationStatistics {
        total: count_all(pool, TABLE).await?,
        pending: count_by_status(pool, TABLE, "PENDING").await?,
        approved: count_by_status(pool, TABLE, "APPROVED").await?,
        rejected: count_by_status(pool, TABLE, "REJECTED").await?,
    })
}

pub async fn group_statistics(pool: &PgPool) -> Result<GroupStatistics, sqlx::Error> {
    Ok(GroupStatistics {
        total: count_all(pool, "groups").await?,
        total_members: count_all(pool, "group_member").await?,
    })
}

pub async fn friendship_statistics(pool: &PgPool) -> Result<FriendshipStatistics, sqlx::Error> {
    const TABLE: &str = "friendship_requests";
    Ok(FriendshipStatistics {
        total: count_all(pool, TABLE).await?,
        pending: count_by_status(pool, TABLE, FriendshipStatus::Pending.as_str()).await?,
        accepted: count_by_status(pool, TABLE, FriendshipStatus::Accepted.as_str()).await?,
        declined: count_by_status(pool, TABLE, FriendshipStatus::Declined.as_str()).await?,
    })
}

pub async fn message_statistics(pool: &PgPool) -> Result<Total, sqlx::Error> {
    Ok(Total {
        total: count_all(pool, "message").await?,
    })
}

pub async fn push_subscription_statistics(pool: &PgPool) -> Result<Total, sqlx::Error> {
    Ok(Total {
        total: count_all(pool, "pushsubscription").await?,
    })
}
