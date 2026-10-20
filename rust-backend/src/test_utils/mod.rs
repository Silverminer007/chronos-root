// Test utilities module - available during testing only
pub mod auth;
pub mod db;
pub mod fixtures;
pub mod http_client;
pub mod keycloak;

pub use auth::TestAuthHelper;
pub use db::{TestDb, TestDbConfig};
pub use fixtures::{AppointmentFixture, TestFixtures};
pub use http_client::TestHttpClient;
pub use keycloak::{user_state_for_tests, FakeKeycloak};
