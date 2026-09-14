// Test utilities module - available during testing only
pub mod db;
pub mod fixtures;
pub mod auth;
pub mod http_client;

pub use db::{TestDb, TestDbConfig};
pub use fixtures::{TestFixtures, AppointmentFixture};
pub use auth::TestAuthHelper;
pub use http_client::TestHttpClient;
