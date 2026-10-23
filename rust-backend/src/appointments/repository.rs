use crate::appointments::models::{
    Appointment, AppointmentStatus, Message, ParticipantRow, ParticipationStatus, UserRole,
};
use crate::groups::repository::FriendshipRepository;
use crate::users::identity::{IdentityRepository, UserIdentity};
use chrono::{DateTime, Utc};
use sqlx::PgPool;
use std::collections::HashMap;

/// Columns of the Java-compatible `appointment` table, aliased to the field names of `Appointment`.
/// `status` is the ordinal of the Java enum; a NULL (possible in the Java schema) means PLANNED.
pub const APPOINTMENT_COLUMNS: &str = "id, name, description, start_time, end_time, venue, COALESCE(status, 0)::smallint AS status, minimalattendees AS minimal_attendees, createdat AS created_at, lastupdate AS last_update";
const APPOINTMENT_COLUMNS_ALIASED: &str = "a.id, a.name, a.description, a.start_time, a.end_time, a.venue, COALESCE(a.status, 0)::smallint AS status, a.minimalattendees AS minimal_attendees, a.createdat AS created_at, a.lastupdate AS last_update";

/// Appointments that are neither DELETED (2) nor CANCELLED (1); only for the reminder scheduler
const ACTIVE: &str = "COALESCE(status, 0) NOT IN (1, 2)";

/// Appointments that are not DELETED (2); CANCELLED ones stay visible (Java `status != DELETED`)
const NOT_DELETED: &str = "COALESCE(status, 0) <> 2";
const NOT_DELETED_ALIASED: &str = "COALESCE(a.status, 0) <> 2";

/// Errors that can occur in the repository layer
#[derive(Debug)]
pub enum RepositoryError {
    DatabaseError(String),
    NotFound,
    InvalidInput(String),
}

impl std::fmt::Display for RepositoryError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            RepositoryError::DatabaseError(msg) => write!(f, "Database error: {}", msg),
            RepositoryError::NotFound => write!(f, "Not found"),
            RepositoryError::InvalidInput(msg) => write!(f, "Invalid input: {}", msg),
        }
    }
}

impl std::error::Error for RepositoryError {}

/// AppointmentRepository provides data access for Appointment entities
pub struct AppointmentRepository {
    pool: PgPool,
}

impl AppointmentRepository {
    /// Create a new AppointmentRepository
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Resolves the identities of the given users (deleted placeholder for users without a profile)
    pub async fn find_identities(
        &self,
        oidc_ids: &[String],
    ) -> Result<HashMap<String, UserIdentity>, RepositoryError> {
        IdentityRepository::find_by_ids(&self.pool, oidc_ids)
            .await
            .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find an appointment by its ID
    pub async fn find_by_id(&self, id: i64) -> Result<Option<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(&format!(
            "SELECT {} FROM appointment WHERE id = $1 AND {}",
            APPOINTMENT_COLUMNS, NOT_DELETED
        ))
        .bind(id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find an appointment by its ID without filtering by status
    /// Used for operations that should work on any appointment regardless of status (e.g., adding participants)
    pub async fn find_by_id_any_status(
        &self,
        id: i64,
    ) -> Result<Option<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(&format!(
            "SELECT {} FROM appointment WHERE id = $1",
            APPOINTMENT_COLUMNS
        ))
        .bind(id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// A page of all appointments of any status (Java: `findAll().page(..)`), ordered by id
    pub async fn find_page(
        &self,
        offset: i64,
        limit: i64,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(&format!(
            "SELECT {} FROM appointment ORDER BY id LIMIT $1 OFFSET $2",
            APPOINTMENT_COLUMNS
        ))
        .bind(limit)
        .bind(offset)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Number of appointments of any status
    pub async fn count(&self) -> Result<i64, RepositoryError> {
        sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM appointment")
            .fetch_one(&self.pool)
            .await
            .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Store a chat message; returns its id. The id comes from the column default, the same
    /// source as `persist_message` (a second sequence would collide on the primary key).
    pub async fn insert_message(
        &self,
        appointment_id: Option<i64>,
        body: &str,
        sender_id: &str,
        timestamp: DateTime<Utc>,
    ) -> Result<i64, RepositoryError> {
        sqlx::query_scalar::<_, i64>(
            "INSERT INTO message (appointment_id, body, sender_oidcid, \"timestamp\")
             VALUES ($1, $2, $3, $4)
             RETURNING id",
        )
        .bind(appointment_id)
        .bind(body)
        .bind(sender_id)
        .bind(timestamp)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find appointments created by a user (the user is a RESPONSIBLE participant)
    pub async fn find_by_creator(
        &self,
        creator_id: &str,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(&format!(
            "SELECT {} FROM appointment a
             JOIN appointment_participation ap ON a.id = ap.appointment_id
             WHERE ap.user_oidcid = $1 AND ap.role = 'RESPONSIBLE' AND {}
             ORDER BY a.start_time DESC",
            APPOINTMENT_COLUMNS_ALIASED, NOT_DELETED_ALIASED
        ))
        .bind(creator_id)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// One page of the appointments a user participates in (Java: `AppointmentRepository#search`):
    /// not DELETED, ending after `after` and starting before `before`, optionally matching
    /// `search` in name, description or venue (case-insensitive substring), ordered by start.
    /// Returns the page and the total number of matches.
    pub async fn search_visible(
        &self,
        user_id: &str,
        search: Option<&str>,
        after: DateTime<Utc>,
        before: DateTime<Utc>,
        page: i64,
        size: i64,
    ) -> Result<(Vec<Appointment>, i64), RepositoryError> {
        let db_error = |e: sqlx::Error| RepositoryError::DatabaseError(e.to_string());
        let pattern = search.map(|query| format!("%{query}%"));
        let deleted = AppointmentStatus::Deleted.ordinal();
        let filter = format!(
            "EXISTS (SELECT 1 FROM appointment_participation ap
                     WHERE ap.appointment_id = a.id AND ap.user_oidcid = $1)
             AND a.end_time > $2 AND a.start_time < $3 AND COALESCE(a.status, 0) <> {deleted}
             AND ($4::TEXT IS NULL OR lower(a.name) LIKE lower($4)
                  OR lower(a.description) LIKE lower($4) OR lower(a.venue) LIKE lower($4))"
        );

        let total = sqlx::query_scalar::<_, i64>(&format!(
            "SELECT COUNT(*) FROM appointment a WHERE {filter}"
        ))
        .bind(user_id)
        .bind(after)
        .bind(before)
        .bind(&pattern)
        .fetch_one(&self.pool)
        .await
        .map_err(db_error)?;

        let items = sqlx::query_as::<_, Appointment>(&format!(
            "SELECT {APPOINTMENT_COLUMNS_ALIASED} FROM appointment a WHERE {filter}
             ORDER BY a.start_time, a.id LIMIT $5 OFFSET $6"
        ))
        .bind(user_id)
        .bind(after)
        .bind(before)
        .bind(&pattern)
        .bind(size)
        .bind(page.saturating_mul(size))
        .fetch_all(&self.pool)
        .await
        .map_err(db_error)?;
        Ok((items, total))
    }

    /// Appointments starting strictly between `after` and `before` that are neither CANCELLED nor
    /// DELETED (Java `findNonCancelledBetween`); the reminder scheduler's input.
    pub async fn find_non_cancelled_between(
        &self,
        after: DateTime<Utc>,
        before: DateTime<Utc>,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(&format!(
            "SELECT {} FROM appointment WHERE start_time > $1 AND start_time < $2 AND {} ORDER BY start_time ASC",
            APPOINTMENT_COLUMNS, ACTIVE
        ))
        .bind(after)
        .bind(before)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Find appointments by date range
    pub async fn find_by_date_range(
        &self,
        start: DateTime<Utc>,
        end: DateTime<Utc>,
    ) -> Result<Vec<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(&format!(
            "SELECT {} FROM appointment WHERE start_time >= $1 AND end_time <= $2 AND {} ORDER BY start_time ASC",
            APPOINTMENT_COLUMNS, NOT_DELETED
        ))
        .bind(start)
        .bind(end)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Whether an accepted friendship exists between two users
    pub async fn are_friends(&self, user1_id: &str, user2_id: &str) -> Result<bool, sqlx::Error> {
        FriendshipRepository::are_friends(&self.pool, user1_id, user2_id).await
    }

    /// Create a new appointment; the creator becomes its RESPONSIBLE participant in the same
    /// transaction (the Java schema has no creator column).
    #[allow(clippy::too_many_arguments)]
    pub async fn create(
        &self,
        name: String,
        description: Option<String>,
        venue: Option<String>,
        start_time: DateTime<Utc>,
        end_time: DateTime<Utc>,
        creator_id: &str,
        minimal_attendees: Option<i32>,
    ) -> Result<Appointment, RepositoryError> {
        let now = chrono::Utc::now();
        let db_err = |e: sqlx::Error| RepositoryError::DatabaseError(e.to_string());

        let mut tx = self.pool.begin().await.map_err(db_err)?;

        let appointment = sqlx::query_as::<_, Appointment>(&format!(
            "INSERT INTO appointment (id, name, description, start_time, end_time, venue, status, minimalattendees, createdat, lastupdate)
             VALUES (nextval('appointment_seq'), $1, $2, $3, $4, $5, $6, $7, $8, $8)
             RETURNING {}",
            APPOINTMENT_COLUMNS
        ))
        .bind(name)
        .bind(description)
        .bind(start_time)
        .bind(end_time)
        .bind(venue)
        .bind(AppointmentStatus::Planned)
        .bind(minimal_attendees)
        .bind(now)
        .fetch_one(&mut *tx)
        .await
        .map_err(db_err)?;

        sqlx::query(
            "INSERT INTO appointment_participation (id, appointment_id, user_oidcid, role, status)
             VALUES (nextval('appointment_participation_seq'), $1, $2, 'RESPONSIBLE', 'PENDING')",
        )
        .bind(appointment.id)
        .bind(creator_id)
        .execute(&mut *tx)
        .await
        .map_err(db_err)?;

        tx.commit().await.map_err(db_err)?;
        Ok(appointment)
    }

    /// Update an appointment
    #[allow(clippy::too_many_arguments)]
    pub async fn update(
        &self,
        id: i64,
        name: Option<String>,
        description: Option<String>,
        venue: Option<String>,
        start_time: Option<DateTime<Utc>>,
        end_time: Option<DateTime<Utc>>,
        minimal_attendees: Option<i32>,
    ) -> Result<Option<Appointment>, RepositoryError> {
        let now = chrono::Utc::now();

        // Build dynamic query based on which fields are provided
        let mut query_str = "UPDATE appointment SET lastupdate = $1".to_string();
        let mut param_count = 1;

        if name.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", name = ${}", param_count));
        }
        if description.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", description = ${}", param_count));
        }
        if venue.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", venue = ${}", param_count));
        }
        if start_time.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", start_time = ${}", param_count));
        }
        if end_time.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", end_time = ${}", param_count));
        }
        if minimal_attendees.is_some() {
            param_count += 1;
            query_str.push_str(&format!(", minimalattendees = ${}", param_count));
        }

        param_count += 1;
        query_str.push_str(&format!(" WHERE id = ${}", param_count));
        query_str.push_str(&format!(" RETURNING {}", APPOINTMENT_COLUMNS));

        let mut query = sqlx::query_as::<_, Appointment>(&query_str).bind(now);

        if let Some(n) = name {
            query = query.bind(n);
        }
        if let Some(d) = description {
            query = query.bind(d);
        }
        if let Some(v) = venue {
            query = query.bind(v);
        }
        if let Some(st) = start_time {
            query = query.bind(st);
        }
        if let Some(et) = end_time {
            query = query.bind(et);
        }
        if let Some(ma) = minimal_attendees {
            query = query.bind(ma);
        }

        query = query.bind(id);

        query
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Soft delete an appointment (set status to DELETED)
    pub async fn delete_soft(&self, id: i64) -> Result<Option<Appointment>, RepositoryError> {
        self.set_status(id, AppointmentStatus::Deleted).await
    }

    /// Soft cancel an appointment (set status to CANCELLED)
    pub async fn cancel_soft(&self, id: i64) -> Result<Option<Appointment>, RepositoryError> {
        self.set_status(id, AppointmentStatus::Cancelled).await
    }

    async fn set_status(
        &self,
        id: i64,
        status: AppointmentStatus,
    ) -> Result<Option<Appointment>, RepositoryError> {
        sqlx::query_as::<_, Appointment>(&format!(
            "UPDATE appointment SET status = $1, lastupdate = NOW() WHERE id = $2 RETURNING {}",
            APPOINTMENT_COLUMNS
        ))
        .bind(status)
        .bind(id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Check if a user is a participant in an appointment
    pub async fn is_participant(
        &self,
        appointment_id: i64,
        user_id: &str,
    ) -> Result<bool, RepositoryError> {
        sqlx::query_scalar::<_, bool>(
            "SELECT EXISTS(SELECT 1 FROM appointment_participation WHERE appointment_id = $1 AND user_oidcid = $2)"
        )
        .bind(appointment_id)
        .bind(user_id)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Get the role of a participant in an appointment
    pub async fn get_participant_role(
        &self,
        appointment_id: i64,
        user_id: &str,
    ) -> Result<Option<UserRole>, RepositoryError> {
        sqlx::query_scalar::<_, UserRole>(
            "SELECT role FROM appointment_participation WHERE appointment_id = $1 AND user_oidcid = $2",
        )
        .bind(appointment_id)
        .bind(user_id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Messages of an appointment, newest first (Java: `MessageRepository#listByAppointment`).
    /// Messages are hard-deleted in Java, so there is no soft-delete filter.
    pub async fn find_messages(
        &self,
        appointment_id: i64,
    ) -> Result<Vec<Message>, RepositoryError> {
        sqlx::query_as::<_, Message>(
            "SELECT id, sender_oidcid, appointment_id, body, \"timestamp\" FROM message
             WHERE appointment_id = $1 ORDER BY \"timestamp\" DESC, id DESC",
        )
        .bind(appointment_id)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Stores a message and returns the stored row (Java: `MessageService#persistMessage`).
    /// The timestamp is the database time, `timestamptz(6)` like Java's `Instant.now()`.
    pub async fn persist_message(
        &self,
        appointment_id: i64,
        sender_oidcid: &str,
        body: Option<&str>,
    ) -> Result<Message, RepositoryError> {
        sqlx::query_as::<_, Message>(
            "INSERT INTO message (appointment_id, sender_oidcid, body, \"timestamp\")
             VALUES ($1, $2, $3, now())
             RETURNING id, sender_oidcid, appointment_id, body, \"timestamp\"",
        )
        .bind(appointment_id)
        .bind(sender_oidcid)
        .bind(body)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Get the participation status of a user for an appointment (None if no participation row)
    pub async fn find_participation_status(
        &self,
        appointment_id: i64,
        user_id: &str,
    ) -> Result<Option<ParticipationStatus>, RepositoryError> {
        // The status column is VARCHAR, but ParticipationStatus declares `sqlx(type_name = "TEXT")`, which rejects a VARCHAR column, so it is cast to TEXT
        sqlx::query_scalar::<_, ParticipationStatus>(
            "SELECT status::TEXT FROM appointment_participation WHERE appointment_id = $1 AND user_oidcid = $2",
        )
        .bind(appointment_id)
        .bind(user_id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// All participations of an appointment (oldest first), with the name of the group they came from.
    /// Java stores the *group id* in `group_participation_id` and resolves the name via `groups`.
    pub async fn list_participants(
        &self,
        appointment_id: i64,
    ) -> Result<Vec<ParticipantRow>, RepositoryError> {
        self.list_participants_of(&[appointment_id]).await
    }

    /// The participations of several appointments (by appointment, then oldest first)
    pub async fn list_participants_of(
        &self,
        appointment_ids: &[i64],
    ) -> Result<Vec<ParticipantRow>, RepositoryError> {
        // status is VARCHAR; see find_participation_status for the TEXT cast
        sqlx::query_as::<_, ParticipantRow>(
            "SELECT ap.appointment_id, ap.user_oidcid, ap.role, ap.status::TEXT AS status,
                    ap.group_participation_id, g.groupname AS group_name
             FROM appointment_participation ap
             LEFT JOIN groups g ON g.id = ap.group_participation_id
             WHERE ap.appointment_id = ANY($1)
             ORDER BY ap.appointment_id, ap.id",
        )
        .bind(appointment_ids)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// The messages of several appointments, oldest first (no soft-delete filter, see `find_messages`)
    pub async fn find_messages_of(
        &self,
        appointment_ids: &[i64],
    ) -> Result<Vec<Message>, RepositoryError> {
        sqlx::query_as::<_, Message>(
            "SELECT id, sender_oidcid, appointment_id, body, \"timestamp\" FROM message
             WHERE appointment_id = ANY($1) ORDER BY \"timestamp\", id",
        )
        .bind(appointment_ids)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// The groups of several appointments as `(appointment_id, group_id, group name)`
    pub async fn list_group_participations(
        &self,
        appointment_ids: &[i64],
    ) -> Result<Vec<(i64, i64, Option<String>)>, RepositoryError> {
        sqlx::query_as::<_, (i64, i64, Option<String>)>(
            "SELECT agp.appointment_id, agp.group_id, g.groupname
             FROM appointment_group_participations agp
             JOIN groups g ON g.id = agp.group_id
             WHERE agp.appointment_id = ANY($1)
             ORDER BY agp.appointment_id, agp.id",
        )
        .bind(appointment_ids)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// The members of several groups as `(group_id, user_oidcid)`
    pub async fn list_group_members(
        &self,
        group_ids: &[i64],
    ) -> Result<Vec<(i64, String)>, RepositoryError> {
        sqlx::query_as::<_, (i64, String)>(
            "SELECT group_id, user_oidcid FROM group_member
             WHERE group_id = ANY($1) ORDER BY group_id, id",
        )
        .bind(group_ids)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Update a user's participation status; NotFound if the user has no participation row
    pub async fn update_participation_status(
        &self,
        appointment_id: i64,
        user_id: &str,
        new_status: ParticipationStatus,
    ) -> Result<(), RepositoryError> {
        let result = sqlx::query(
            "UPDATE appointment_participation SET status = $1
             WHERE appointment_id = $2 AND user_oidcid = $3",
        )
        .bind(new_status.to_string())
        .bind(appointment_id)
        .bind(user_id)
        .execute(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?;

        if result.rows_affected() == 0 {
            return Err(RepositoryError::NotFound);
        }

        Ok(())
    }

    /// Add a participant to an appointment (no-op if the user already participates)
    pub async fn add_participant(
        &self,
        appointment_id: i64,
        user_id: &str,
        role: &str,
        status: &str,
    ) -> Result<(), RepositoryError> {
        sqlx::query(
            "INSERT INTO appointment_participation (id, appointment_id, user_oidcid, role, status)
             VALUES (nextval('appointment_participation_seq'), $1, $2, $3, $4)
             ON CONFLICT (appointment_id, user_oidcid) DO NOTHING",
        )
        .bind(appointment_id)
        .bind(user_id)
        .bind(role)
        .bind(status)
        .execute(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    /// Add a participant to an appointment, returning error if already exists
    pub async fn add_participant_strict(
        &self,
        appointment_id: i64,
        user_id: &str,
        role: &str,
        status: &str,
    ) -> Result<(), RepositoryError> {
        // Check if user is already a participant
        if self.is_participant(appointment_id, user_id).await? {
            return Err(RepositoryError::InvalidInput(
                "This user is already a participant of this appointment".to_string(),
            ));
        }

        sqlx::query(
            "INSERT INTO appointment_participation (id, appointment_id, user_oidcid, role, status)
             VALUES (nextval('appointment_participation_seq'), $1, $2, $3, $4)",
        )
        .bind(appointment_id)
        .bind(user_id)
        .bind(role)
        .bind(status)
        .execute(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    /// Set the role of an existing participant
    pub async fn update_participant_role(
        &self,
        appointment_id: i64,
        user_id: &str,
        role: UserRole,
    ) -> Result<(), RepositoryError> {
        let result = sqlx::query(
            "UPDATE appointment_participation SET role = $3 WHERE appointment_id = $1 AND user_oidcid = $2",
        )
        .bind(appointment_id)
        .bind(user_id)
        .bind(role)
        .execute(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?;

        if result.rows_affected() == 0 {
            return Err(RepositoryError::NotFound);
        }

        Ok(())
    }

    /// Remove a participant from an appointment (hard delete)
    pub async fn remove_participant(
        &self,
        appointment_id: i64,
        user_id: &str,
    ) -> Result<(), RepositoryError> {
        let result = sqlx::query(
            "DELETE FROM appointment_participation WHERE appointment_id = $1 AND user_oidcid = $2",
        )
        .bind(appointment_id)
        .bind(user_id)
        .execute(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))?;

        if result.rows_affected() == 0 {
            return Err(RepositoryError::NotFound);
        }

        Ok(())
    }

    /// Check if a group exists
    pub async fn group_exists(&self, group_id: i64) -> Result<bool, RepositoryError> {
        sqlx::query_scalar::<_, bool>("SELECT EXISTS(SELECT 1 FROM groups WHERE id = $1)")
            .bind(group_id)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Check if a user is a member of a group
    pub async fn is_group_member(
        &self,
        group_id: i64,
        user_id: &str,
    ) -> Result<bool, RepositoryError> {
        sqlx::query_scalar::<_, bool>(
            "SELECT EXISTS(SELECT 1 FROM group_member WHERE group_id = $1 AND user_oidcid = $2)",
        )
        .bind(group_id)
        .bind(user_id)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Check if a group is already a participant in an appointment
    pub async fn group_participation_exists(
        &self,
        appointment_id: i64,
        group_id: i64,
    ) -> Result<bool, RepositoryError> {
        sqlx::query_scalar::<_, bool>(
            "SELECT EXISTS(SELECT 1 FROM appointment_group_participations WHERE appointment_id = $1 AND group_id = $2)",
        )
        .bind(appointment_id)
        .bind(group_id)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Adds a group to an appointment and makes its members PENDING participants with the given
    /// role, marked as coming in through the group (Java: `addGroupToAppointment`). Both writes run
    /// in one transaction, so a failing member insert leaves no group link behind. Users that
    /// already participate keep their row.
    pub async fn add_group_with_members(
        &self,
        appointment_id: i64,
        group_id: i64,
        role: UserRole,
    ) -> Result<(), RepositoryError> {
        let db_err = |e: sqlx::Error| RepositoryError::DatabaseError(e.to_string());
        let mut tx = self.pool.begin().await.map_err(db_err)?;
        sqlx::query(
            "INSERT INTO appointment_group_participations (id, appointment_id, group_id, role)
             VALUES (nextval('appointment_group_participations_seq'), $1, $2, $3)",
        )
        .bind(appointment_id)
        .bind(group_id)
        .bind(role)
        .execute(&mut *tx)
        .await
        .map_err(db_err)?;
        sqlx::query(
            "INSERT INTO appointment_participation
                 (id, appointment_id, user_oidcid, role, status, group_participation_id)
             SELECT nextval('appointment_participation_seq'), $1, gm.user_oidcid, $3, 'PENDING', $2
             FROM (SELECT DISTINCT user_oidcid FROM group_member WHERE group_id = $2) gm
             ON CONFLICT (appointment_id, user_oidcid) DO NOTHING",
        )
        .bind(appointment_id)
        .bind(group_id)
        .bind(role)
        .execute(&mut *tx)
        .await
        .map_err(db_err)?;
        tx.commit().await.map_err(db_err)
    }

    /// Delete the link between a group and an appointment; returns the number of deleted rows
    /// (Java: `deleteGroupParticipation`).
    pub async fn remove_group(
        &self,
        appointment_id: i64,
        group_id: i64,
    ) -> Result<u64, RepositoryError> {
        sqlx::query(
            "DELETE FROM appointment_group_participations WHERE appointment_id = $1 AND group_id = $2",
        )
        .bind(appointment_id)
        .bind(group_id)
        .execute(&self.pool)
        .await
        .map(|r| r.rows_affected())
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }

    /// Delete every participation of the appointment that came in through the group; directly
    /// added participants keep their row (Java: `deleteByGroupAndAppointment`).
    pub async fn remove_group_participants(
        &self,
        appointment_id: i64,
        group_id: i64,
    ) -> Result<u64, RepositoryError> {
        sqlx::query(
            "DELETE FROM appointment_participation WHERE appointment_id = $1 AND group_participation_id = $2",
        )
        .bind(appointment_id)
        .bind(group_id)
        .execute(&self.pool)
        .await
        .map(|r| r.rows_affected())
        .map_err(|e| RepositoryError::DatabaseError(e.to_string()))
    }
}
