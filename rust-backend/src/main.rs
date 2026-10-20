use std::net::SocketAddr;
use std::sync::Arc;
use tracing::{error, info};

use chronos_date_api::app::build_router;
use chronos_date_api::appointments::event_listeners::AppointmentParticipationListener;
use chronos_date_api::appointments::handlers::AppState;
use chronos_date_api::database::{init_pool, DatabaseConfig};
use chronos_date_api::event_bus::postgres::PostgresEventBus;
use chronos_date_api::keycloak::{HttpKeycloakAdmin, KeycloakConfig};
use chronos_date_api::push_notifications::{
    NotificationDispatcher, PushEventHandler, PushNotificationEventListener, PushSender, PushState,
    VapidConfig, WebPushSender,
};
use chronos_date_api::security::TokenValidator;
use chronos_date_api::users::UserState;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();

    let config = match DatabaseConfig::from_env() {
        Ok(cfg) => cfg,
        Err(e) => {
            error!("Database configuration error: {}", e);
            std::process::exit(1);
        }
    };

    let pool = match init_pool(config).await {
        Ok(p) => p,
        Err(e) => {
            error!("Failed to initialize database pool: {}", e);
            std::process::exit(1);
        }
    };

    let keycloak_config = match KeycloakConfig::from_env() {
        Ok(cfg) => cfg,
        Err(e) => {
            error!("Keycloak configuration error: {}", e);
            std::process::exit(1);
        }
    };

    let keycloak_url = std::env::var("KEYCLOAK_URL")
        .unwrap_or_else(|_| "http://localhost:8080/realms/chronos".to_string());
    let validator = Arc::new(TokenValidator::new(keycloak_url));

    // Initialize event bus
    let event_bus = Arc::new(PostgresEventBus::new(pool.clone()));

    // Subscribe to appointment events
    let listener = AppointmentParticipationListener::new(pool.clone());
    if let Err(e) = listener.subscribe_to_events(event_bus.as_ref()).await {
        tracing::error!("Failed to subscribe to appointment events: {:?}", e);
    }

    // Push notifications: without VAPID keys no notification is sent (like Java)
    let vapid = VapidConfig::from_env();
    let push_sender: Option<Arc<dyn PushSender>> = match &vapid {
        Some(config) => match WebPushSender::new(config.clone()) {
            Ok(sender) => Some(Arc::new(sender)),
            Err(e) => {
                error!(
                    "Push notifications disabled, invalid VAPID configuration: {}",
                    e
                );
                None
            }
        },
        None => {
            tracing::warn!(
                "VAPID keys are not set. No push notifications will be sent. \
                 Please set VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY and VAPID_SUBJECT"
            );
            None
        }
    };
    let push_handler = Arc::new(PushEventHandler::new(
        pool.clone(),
        Arc::new(NotificationDispatcher::new(pool.clone(), push_sender)),
    ));
    if let Err(e) = PushNotificationEventListener::new(push_handler)
        .subscribe_all(event_bus.as_ref())
        .await
    {
        error!("Failed to subscribe to push notification events: {:?}", e);
    }
    let push_state = Arc::new(PushState::new(
        pool.clone(),
        vapid.map(|config| config.public_key).unwrap_or_default(),
    ));

    // Create application state
    let app_state = Arc::new(AppState {
        db_pool: pool.clone(),
        event_publisher: event_bus,
    });

    let user_state = Arc::new(UserState {
        db_pool: pool.clone(),
        keycloak_urls: keycloak_config.urls(),
        keycloak: Arc::new(HttpKeycloakAdmin::new(keycloak_config)),
    });

    let app = build_router(app_state, user_state, push_state, validator);

    let port = std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse::<u16>().ok())
        .unwrap_or(8080);

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    info!("Starting server on {}", addr);

    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .expect("Failed to bind to port");

    axum::serve(listener, app).await.expect("Server error");
}
