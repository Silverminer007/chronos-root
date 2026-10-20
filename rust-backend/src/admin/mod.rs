//! admin feature module: `/api/v2/admin/*` (Java: the `Admin*Resource` classes).
//! See ADR-2026-10-20-023 for the authorization layer and the parity decisions.

pub mod handlers;
pub mod models;
pub mod repository;

use std::sync::Arc;

use axum::routing::{get, post};
use axum::Router;

use crate::appointments::handlers::AppState;
use crate::event_bus::EventPublisher;
use crate::keycloak::KeycloakAdmin;
use crate::push_notifications::services::NotificationDispatcher;
use crate::push_notifications::PushState;
use crate::users::UserState;

use handlers::{
    add_group_member, add_group_participant, befriend, change_participation_status,
    create_appointment, create_group, delete_user, get_statistics, get_user, list_appointments,
    list_push_log, list_users, send_message, send_test_push,
};

/// Shared state of the admin handlers: it reuses the collaborators of the other features
#[derive(Clone)]
pub struct AdminState {
    pub db_pool: sqlx::PgPool,
    pub event_publisher: Arc<dyn EventPublisher>,
    pub keycloak: Arc<dyn KeycloakAdmin>,
    pub push: Arc<NotificationDispatcher>,
}

impl AdminState {
    pub fn new(app: &AppState, users: &UserState, push: &PushState) -> Self {
        Self {
            db_pool: app.db_pool.clone(),
            event_publisher: app.event_publisher.clone(),
            keycloak: users.keycloak.clone(),
            push: push.dispatcher.clone(),
        }
    }
}

/// Every route is declared with and without the trailing slash: the Java resources use `@Path("/")`
fn with_trailing_slash(
    router: Router<Arc<AdminState>>,
    path: &str,
    methods: axum::routing::MethodRouter<Arc<AdminState>>,
) -> Router<Arc<AdminState>> {
    router
        .route(path, methods.clone())
        .route(&format!("{path}/"), methods)
}

/// `/api/v2/admin/*`. Authorization is enforced by the `AdminPrincipal` extractor of each handler.
pub fn admin_routes(state: Arc<AdminState>) -> Router {
    let router = Router::new();
    let router = with_trailing_slash(
        router,
        "/api/v2/admin/appointments",
        get(list_appointments).post(create_appointment),
    );
    let router = with_trailing_slash(router, "/api/v2/admin/groups", post(create_group));
    let router = with_trailing_slash(router, "/api/v2/admin/stats", get(get_statistics));
    let router = with_trailing_slash(router, "/api/v2/admin/users", get(list_users));
    router
        .route(
            "/api/v2/admin/appointments/:appointment_id/participants/groups",
            post(add_group_participant),
        )
        .route(
            "/api/v2/admin/appointments/participants/status",
            post(change_participation_status),
        )
        .route("/api/v2/admin/appointments/message", post(send_message))
        .route("/api/v2/admin/friendship/befriend", post(befriend))
        .route(
            "/api/v2/admin/groups/:group_id/users/:user_id",
            post(add_group_member),
        )
        .route("/api/v2/admin/push/test/:user_id", post(send_test_push))
        .route("/api/v2/admin/push/log", get(list_push_log))
        .route("/api/v2/admin/users/:id", get(get_user).delete(delete_user))
        .with_state(state)
}
