use chrono::{DateTime, Utc};
use sqlx::PgPool;

/// Test fixture for creating reproducible test data in the Java-compatible schema.
/// Users are OIDC subjects (rows of `user_profiles`); ids come from the `<table>_seq` sequences.
#[derive(Clone, Debug)]
pub struct TestFixtures {
    pool: PgPool,
}

impl TestFixtures {
    /// Create a new fixture helper
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Create a test user: a `user_profiles` row keyed by the given OIDC subject.
    /// Returns the OIDC subject (the user's id).
    pub async fn create_user(
        &self,
        oidc_id: &str,
        email: &str,
    ) -> Result<String, Box<dyn std::error::Error>> {
        sqlx::query(
            "INSERT INTO user_profiles (oidc_id, first_name, last_name, email) VALUES ($1, $2, $3, $4)",
        )
        .bind(oidc_id)
        .bind("Test")
        .bind("User")
        .bind(email)
        .execute(&self.pool)
        .await?;

        Ok(oidc_id.to_string())
    }

    /// Create a test group
    pub async fn create_group(
        &self,
        owner_id: &str,
        name: &str,
    ) -> Result<i64, Box<dyn std::error::Error>> {
        let group_id = sqlx::query_scalar::<_, i64>(
            "INSERT INTO groups (id, groupname, owner_oidcid) VALUES (nextval('groups_seq'), $1, $2) RETURNING id",
        )
        .bind(name)
        .bind(owner_id)
        .fetch_one(&self.pool)
        .await?;

        Ok(group_id)
    }

    /// Create a test appointment; the creator becomes its RESPONSIBLE participant
    pub async fn create_appointment(
        &self,
        creator_id: &str,
        fixture: &AppointmentFixture,
    ) -> Result<i64, Box<dyn std::error::Error>> {
        let appointment_id = match fixture.id {
            Some(id) => id,
            None => {
                sqlx::query_scalar::<_, i64>("SELECT nextval('appointment_seq')")
                    .fetch_one(&self.pool)
                    .await?
            }
        };
        sqlx::query(
            "INSERT INTO appointment (id, name, description, start_time, end_time, venue, status, createdat, lastupdate)
             VALUES ($1, $2, $3, $4, $5, $6, 0, NOW(), NOW())",
        )
        .bind(appointment_id)
        .bind(&fixture.title)
        .bind(&fixture.description)
        .bind(fixture.start_time)
        .bind(fixture.end_time)
        .bind(&fixture.location)
        .execute(&self.pool)
        .await?;

        sqlx::query(
            "INSERT INTO appointment_participation (id, appointment_id, user_oidcid, role, status)
             VALUES (nextval('appointment_participation_seq'), $1, $2, 'RESPONSIBLE', 'APPROVED')",
        )
        .bind(appointment_id)
        .bind(creator_id)
        .execute(&self.pool)
        .await?;

        Ok(appointment_id)
    }

    /// Add an ATTENDANT participant to an appointment
    pub async fn add_participant(
        &self,
        appointment_id: i64,
        user_id: &str,
        status: &str,
    ) -> Result<i64, Box<dyn std::error::Error>> {
        let participant_id = sqlx::query_scalar::<_, i64>(
            "INSERT INTO appointment_participation (id, appointment_id, user_oidcid, role, status)
             VALUES (nextval('appointment_participation_seq'), $1, $2, 'ATTENDANT', $3) RETURNING id",
        )
        .bind(appointment_id)
        .bind(user_id)
        .bind(status)
        .fetch_one(&self.pool)
        .await?;

        Ok(participant_id)
    }
}

/// Builder for appointment test data
#[derive(Clone, Debug)]
pub struct AppointmentFixture {
    pub id: Option<i64>,
    pub title: String,
    pub description: Option<String>,
    pub start_time: DateTime<Utc>,
    pub end_time: DateTime<Utc>,
    pub location: Option<String>,
}

impl AppointmentFixture {
    /// Create a default appointment fixture
    pub fn new() -> Self {
        let now = Utc::now();
        Self {
            id: None,
            title: "Test Appointment".to_string(),
            description: Some("A test appointment".to_string()),
            start_time: now,
            end_time: now + chrono::Duration::hours(1),
            location: Some("Test Location".to_string()),
        }
    }

    /// Set the appointment ID
    pub fn with_id(mut self, id: i64) -> Self {
        self.id = Some(id);
        self
    }

    /// Set the title
    pub fn with_title(mut self, title: &str) -> Self {
        self.title = title.to_string();
        self
    }

    /// Set the description
    pub fn with_description(mut self, description: Option<&str>) -> Self {
        self.description = description.map(|s| s.to_string());
        self
    }

    /// Set start and end times
    pub fn with_times(mut self, start: DateTime<Utc>, end: DateTime<Utc>) -> Self {
        self.start_time = start;
        self.end_time = end;
        self
    }

    /// Set the location
    pub fn with_location(mut self, location: Option<&str>) -> Self {
        self.location = location.map(|s| s.to_string());
        self
    }
}

impl Default for AppointmentFixture {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_appointment_fixture_builder() {
        let fixture = AppointmentFixture::new()
            .with_title("Team Meeting")
            .with_location(Some("Conference Room"));

        assert_eq!(fixture.title, "Team Meeting");
        assert_eq!(fixture.location, Some("Conference Room".to_string()));
    }

    #[test]
    fn test_appointment_fixture_with_id() {
        let fixture = AppointmentFixture::new().with_id(42);
        assert_eq!(fixture.id, Some(42));
    }

    #[test]
    fn test_appointment_fixture_default() {
        let fixture = AppointmentFixture::default();
        assert_eq!(fixture.title, "Test Appointment");
        assert!(fixture.description.is_some());
    }

    #[test]
    fn test_appointment_fixture_times() {
        let now = Utc::now();
        let fixture = AppointmentFixture::new().with_times(now, now + chrono::Duration::hours(2));

        assert_eq!(fixture.start_time, now);
        assert_eq!(fixture.end_time, now + chrono::Duration::hours(2));
    }
}
