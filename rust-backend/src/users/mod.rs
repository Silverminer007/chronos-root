//! users feature module: `/api/v2/user*` and `/api/v2/settings` (Java: `UserResource`, `SettingsResource`).
//! See ADR-2026-10-20-017 for the Keycloak boundary and the Java-parity decisions.

pub mod handlers;
pub mod identity;
pub mod models;
pub mod repository;
pub mod services;

use std::sync::Arc;

use crate::keycloak::{KeycloakAdmin, KeycloakUrls};

/// Shared state of the user and settings handlers
#[derive(Clone)]
pub struct UserState {
    pub db_pool: sqlx::PgPool,
    pub keycloak: Arc<dyn KeycloakAdmin>,
    pub keycloak_urls: KeycloakUrls,
}
