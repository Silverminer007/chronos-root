use chrono::{DateTime, Utc};
use sqlx::PgPool;
use uuid::Uuid;

/// Test fixture for creating reproducible test data
#[derive(Clone, Debug)]
pub struct TestFixtures {
    pool: PgPool,
}

impl TestFixtures {
    /// Create a new fixture helper
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Create a test user
    pub async fn create_user(
        &self,
        keycloak_id: &str,
        email: &str,
    ) -> Result<Uuid, Box<dyn std::error::Error>> {
        let user_id = Uuid::new_v4();
        sqlx::query(
            "INSERT INTO users (id, keycloak_id, email, first_name, last_name) VALUES ($1, $2, $3, $4, $5)"
        )
        .bind(user_id)
        .bind(keycloak_id)
        .bind(email)
        .bind("Test")
        .bind("User")
        .execute(&self.pool)
        .await?;

        Ok(user_id)
    }

    /// Create a test group
    pub async fn create_group(
        &self,
        owner_id: Uuid,
        name: &str,
    ) -> Result<Uuid, Box<dyn std::error::Error>> {
        let group_id = Uuid::new_v4();
        sqlx::query("INSERT INTO groups (id, name, owner_id) VALUES ($1, $2, $3)")
            .bind(group_id)
            .bind(name)
            .bind(owner_id)
            .execute(&self.pool)
            .await?;

        Ok(group_id)
    }

    /// Create a test appointment
    pub async fn create_appointment(
        &self,
        creator_id: Uuid,
        fixture: &AppointmentFixture,
    ) -> Result<Uuid, Box<dyn std::error::Error>> {
        let appointment_id = fixture.id.unwrap_or_else(Uuid::new_v4);
        sqlx::query(
            "INSERT INTO appointments (id, title, description, start_time, end_time, location, creator_id)
             VALUES ($1, $2, $3, $4, $5, $6, $7)"
        )
        .bind(appointment_id)
        .bind(&fixture.title)
        .bind(&fixture.description)
        .bind(fixture.start_time)
        .bind(fixture.end_time)
        .bind(&fixture.location)
        .bind(creator_id)
        .execute(&self.pool)
        .await?;

        Ok(appointment_id)
    }

    /// Add a participant to an appointment
    pub async fn add_participant(
        &self,
        appointment_id: Uuid,
        user_id: Uuid,
        status: &str,
    ) -> Result<Uuid, Box<dyn std::error::Error>> {
        let participant_id = Uuid::new_v4();
        sqlx::query(
            "INSERT INTO appointment_participants (id, appointment_id, user_id, status) VALUES ($1, $2, $3, $4)"
        )
        .bind(participant_id)
        .bind(appointment_id)
        .bind(user_id)
        .bind(status)
        .execute(&self.pool)
        .await?;

        Ok(participant_id)
    }
}

/// Builder for appointment test data
#[derive(Clone, Debug)]
pub struct AppointmentFixture {
    pub id: Option<Uuid>,
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
    pub fn with_id(mut self, id: Uuid) -> Self {
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
