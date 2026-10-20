//! push_notifications feature module: `/api/v2/push/*` (Java: `PushResource`) and the event-driven
//! notifications (Java: `WebPushService`). See ADR-2026-10-20-019.

pub mod events;
pub mod handlers;
pub mod models;
pub mod repository;
pub mod services;

pub use handlers::{PushEventHandler, PushNotificationEventListener, PushState};
pub use models::{PushSubscription, PushSubscriptionRequest};
pub use repository::PushSubscriptionRepository;
pub use services::{
    NotificationDispatcher, PushNotificationError, PushSender, VapidConfig, WebPushSender,
};
