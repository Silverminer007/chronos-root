/// Integration tests for the event-driven push notifications.
///
/// Contract source: the Java `WebPushService` (see ADR-2026-10-20-019). The push service is
/// replaced by a fake so the exact payloads and recipients can be asserted; the notification log
/// is read back from the database.
use async_trait::async_trait;
use chronos_date_api::event_bus::postgres::PostgresEventBus;
use chronos_date_api::event_bus::{Event, EventPublisher};
use chronos_date_api::push_notifications::models::PushSubscription;
use chronos_date_api::push_notifications::services::{NotificationDispatcher, PushSender};
use chronos_date_api::push_notifications::{PushEventHandler, PushNotificationEventListener};
use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures};
use serde_json::{json, Value};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::Duration;

const ALICE: &str = "alice";
const BOB: &str = "bob";
const CAROL: &str = "carol";
const DAVE: &str = "dave";

/// Records every send; the response per endpoint defaults to `201`
#[derive(Default)]
struct FakePushSender {
    sent: Mutex<Vec<(String, Value)>>,
    responses: Mutex<HashMap<String, Result<u16, String>>>,
}

impl FakePushSender {
    fn respond(&self, endpoint: &str, response: Result<u16, String>) {
        self.responses
            .lock()
            .unwrap()
            .insert(endpoint.to_string(), response);
    }

    /// (user, payload) pairs; the user is derived from the endpoint `https://push.test/<user>`
    fn sent(&self) -> Vec<(String, Value)> {
        let mut sent: Vec<(String, Value)> = self
            .sent
            .lock()
            .unwrap()
            .iter()
            .map(|(endpoint, payload)| {
                (
                    endpoint
                        .trim_start_matches("https://push.test/")
                        .to_string(),
                    payload.clone(),
                )
            })
            .collect();
        sent.sort_by(|a, b| a.0.cmp(&b.0));
        sent
    }

    fn recipients(&self) -> Vec<String> {
        self.sent().into_iter().map(|(user, _)| user).collect()
    }
}

#[async_trait]
impl PushSender for FakePushSender {
    async fn send(&self, subscription: &PushSubscription, payload: &str) -> Result<u16, String> {
        self.sent.lock().unwrap().push((
            subscription.endpoint.clone(),
            serde_json::from_str(payload).expect("payload is JSON"),
        ));
        self.responses
            .lock()
            .unwrap()
            .get(&subscription.endpoint)
            .cloned()
            .unwrap_or(Ok(201))
    }
}

/// (user, type, HTTP status, success, error message) of `push_notification_log`
type LogRow = (String, Option<String>, Option<i32>, bool, Option<String>);

struct Setup {
    db: TestDb,
    sender: Arc<FakePushSender>,
    handler: Arc<PushEventHandler>,
    appointment_id: i64,
}

fn endpoint(user: &str) -> String {
    format!("https://push.test/{user}")
}

/// Appointment "Training" with alice RESPONSIBLE/APPROVED, bob ATTENDANT/PENDING,
/// carol GUEST/PENDING and dave RESPONSIBLE/APPROVED; every user has one subscription.
async fn setup() -> Setup {
    let db = TestDb::new().await.expect("test database");
    let fixtures = TestFixtures::new(db.pool().clone());
    for user in [ALICE, BOB, CAROL, DAVE] {
        fixtures
            .create_user(user, &format!("{user}@example.org"))
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO pushsubscription (id, user_oidcid, endpoint, p256dh, auth)
             VALUES (nextval('pushsubscription_seq'), $1, $2, 'p256dh', 'auth')",
        )
        .bind(user)
        .bind(endpoint(user))
        .execute(db.pool())
        .await
        .unwrap();
    }
    let appointment_id = fixtures
        .create_appointment(ALICE, &AppointmentFixture::new().with_title("Training"))
        .await
        .unwrap();
    // The fixture creates the creator PENDING like production; these tests expect ALICE APPROVED
    sqlx::query("UPDATE appointment_participation SET status = 'APPROVED' WHERE user_oidcid = $1")
        .bind(ALICE)
        .execute(db.pool())
        .await
        .unwrap();
    for (user, role, status) in [
        (BOB, "ATTENDANT", "PENDING"),
        (CAROL, "GUEST", "PENDING"),
        (DAVE, "RESPONSIBLE", "APPROVED"),
    ] {
        sqlx::query(
            "INSERT INTO appointment_participation (id, appointment_id, user_oidcid, role, status)
             VALUES (nextval('appointment_participation_seq'), $1, $2, $3, $4)",
        )
        .bind(appointment_id)
        .bind(user)
        .bind(role)
        .bind(status)
        .execute(db.pool())
        .await
        .unwrap();
    }
    let sender = Arc::new(FakePushSender::default());
    let dispatcher = Arc::new(NotificationDispatcher::new(
        db.pool().clone(),
        Some(sender.clone() as Arc<dyn PushSender>),
    ));
    let handler = Arc::new(PushEventHandler::new(db.pool().clone(), dispatcher));
    Setup {
        db,
        sender,
        handler,
        appointment_id,
    }
}

async fn handle(setup: &Setup, event_type: &str, payload: Value) {
    setup
        .handler
        .handle(&Event::new(event_type, payload))
        .await
        .expect("event handled");
}

/// The Java payload carries the appointment as a JSON *string*
fn appointment_of(payload: &Value) -> Value {
    serde_json::from_str(
        payload["appointment"]
            .as_str()
            .expect("appointment is a string"),
    )
    .expect("appointment string is JSON")
}

// ---------------------------------------------------------------- appointment events

#[tokio::test]
async fn moved_notifies_participants_except_the_actor() {
    let s = setup().await;
    handle(
        &s,
        "AppointmentMovedEvent",
        json!({
            "appointment_id": s.appointment_id,
            "old_start": "2026-01-01T10:00:00Z",
            "old_end": "2026-01-01T11:00:00Z",
            "acting_user_id": ALICE,
        }),
    )
    .await;

    // appointment_moved defaults to ALL (GUEST and above)
    assert_eq!(s.sender.recipients(), vec![BOB, CAROL, DAVE]);
    let payload = &s.sender.sent()[0].1;
    assert_eq!(payload["type"], "APPOINTMENT_MOVED");
    assert_eq!(payload["old_start_time"], "2026-01-01T10:00:00Z");
    assert_eq!(payload["old_end_time"], "2026-01-01T11:00:00Z");
    assert_eq!(payload["acting_user_id"], ALICE);
    assert_eq!(payload["acting_user_name"], "Test User");
    let appointment = appointment_of(payload);
    assert_eq!(appointment["id"], s.appointment_id);
    assert_eq!(appointment["name"], "Training");
    assert_eq!(appointment["status"], "PLANNED");
}

#[tokio::test]
async fn cancelled_notifies_participants_except_the_actor() {
    let s = setup().await;
    handle(
        &s,
        "AppointmentCancelledEvent",
        json!({"appointment_id": s.appointment_id, "acting_user_id": ALICE}),
    )
    .await;

    assert_eq!(s.sender.recipients(), vec![BOB, CAROL, DAVE]);
    let payload = &s.sender.sent()[0].1;
    assert_eq!(payload["type"], "APPOINTMENT_CANCELLED");
    assert_eq!(payload["who_cancelled"], "Test User");
}

#[tokio::test]
async fn unknown_acting_user_or_appointment_sends_nothing() {
    let s = setup().await;
    handle(
        &s,
        "AppointmentCancelledEvent",
        json!({"appointment_id": s.appointment_id, "acting_user_id": "nobody"}),
    )
    .await;
    handle(
        &s,
        "AppointmentCancelledEvent",
        json!({"appointment_id": 999_999, "acting_user_id": ALICE}),
    )
    .await;
    assert!(s.sender.sent().is_empty());
}

#[tokio::test]
async fn participant_added_goes_to_responsible_participants_only_by_default() {
    let s = setup().await;
    handle(
        &s,
        "AppointmentParticipationAddedEvent",
        json!({"appointment_id": s.appointment_id, "target_user_id": BOB, "acting_user_id": ALICE}),
    )
    .await;

    // appointment_participant_added defaults to RESPONSIBLE; alice is the actor
    assert_eq!(s.sender.recipients(), vec![DAVE]);
    let payload = &s.sender.sent()[0].1;
    assert_eq!(payload["type"], "NEW_PARTICIPANT");
    assert_eq!(payload["new_participant"], "Test User");
    assert_eq!(payload["participant_type"], "user");
    assert_eq!(payload["participant_role"], "ATTENDANT");
    assert_eq!(payload["acting_user_name"], "Test User");
}

#[tokio::test]
async fn group_participant_added_names_the_group() {
    let s = setup().await;
    let group_id = TestFixtures::new(s.db.pool().clone())
        .create_group(ALICE, "Jugend")
        .await
        .unwrap();
    handle(
        &s,
        "AppointmentGroupParticipationAddedEvent",
        json!({"appointment_id": s.appointment_id, "group_id": group_id, "acting_user_id": ALICE}),
    )
    .await;

    assert_eq!(s.sender.recipients(), vec![DAVE]);
    let payload = &s.sender.sent()[0].1;
    assert_eq!(payload["type"], "NEW_PARTICIPANT");
    assert_eq!(payload["new_participant"], "Jugend");
    assert_eq!(payload["participant_type"], "group");
    assert!(payload.get("participant_role").is_none());
}

#[tokio::test]
async fn status_changed_goes_to_responsible_participants_except_the_actor() {
    let s = setup().await;
    handle(
        &s,
        "AppointmentParticipationStatusChangedEvent",
        json!({
            "appointment_id": s.appointment_id,
            "user_id": BOB,
            "new_status": "APPROVED",
            "old_status": "PENDING",
        }),
    )
    .await;

    // appointment_participation_status_changed defaults to RESPONSIBLE
    assert_eq!(s.sender.recipients(), vec![ALICE, DAVE]);
    let payload = &s.sender.sent()[0].1;
    assert_eq!(payload["type"], "PARTICIPATION_STATUS_CHANGED");
    assert_eq!(payload["new_participation_status"], "APPROVED");
    assert_eq!(payload["user_name"], "Test User");
}

#[tokio::test]
async fn settings_of_the_recipient_decide() {
    let s = setup().await;
    sqlx::query(
        "INSERT INTO settings (user_oidcid, appointment_cancelled) VALUES ($1, 'DISABLED')",
    )
    .bind(CAROL)
    .execute(s.db.pool())
    .await
    .unwrap();
    sqlx::query("INSERT INTO settings (user_oidcid, appointment_cancelled) VALUES ($1, 'HELPER')")
        .bind(BOB)
        .execute(s.db.pool())
        .await
        .unwrap();
    handle(
        &s,
        "AppointmentCancelledEvent",
        json!({"appointment_id": s.appointment_id, "acting_user_id": ALICE}),
    )
    .await;

    // carol disabled it, bob (ATTENDANT) needs at least HELPER
    assert_eq!(s.sender.recipients(), vec![DAVE]);
}

#[tokio::test]
async fn invalid_participation_reports_the_statistics() {
    let s = setup().await;
    sqlx::query("UPDATE appointment_participation SET status = 'REJECTED' WHERE user_oidcid = $1")
        .bind(BOB)
        .execute(s.db.pool())
        .await
        .unwrap();
    handle(
        &s,
        "AppointmentParticipationInvalidEvent",
        json!({"appointment_id": s.appointment_id}),
    )
    .await;

    // appointment_participation_invalid defaults to ATTENDANT
    assert_eq!(s.sender.recipients(), vec![ALICE, BOB, DAVE]);
    let payload = &s.sender.sent()[0].1;
    assert_eq!(payload["type"], "APPOINTMENT_PARTICIPATION_INVALID");
    assert_eq!(payload["approved_participation"], 2);
    assert_eq!(payload["rejected_participation"], 1);
    assert_eq!(payload["pending_participation"], 1);
}

#[tokio::test]
async fn pending_reminder_goes_to_pending_participants_only() {
    let s = setup().await;
    handle(
        &s,
        "AppointmentParticipationStatusPendingReminderEvent",
        json!({"appointment_id": s.appointment_id, "user_id": BOB}),
    )
    .await;

    // carol is PENDING too, but a GUEST (default setting: ATTENDANT)
    assert_eq!(s.sender.recipients(), vec![BOB]);
    assert_eq!(s.sender.sent()[0].1["type"], "PARTICIPATION_STATUS_PENDING");
}

#[tokio::test]
async fn reminder_goes_to_all_participants() {
    let s = setup().await;
    handle(
        &s,
        "AppointmentReminderEvent",
        json!({"appointment_id": s.appointment_id}),
    )
    .await;

    assert_eq!(s.sender.recipients(), vec![ALICE, BOB, CAROL, DAVE]);
    assert_eq!(s.sender.sent()[0].1["type"], "PARTICIPATION_REMINDER");
}

#[tokio::test]
async fn recheck_requested_carries_each_participants_status() {
    let s = setup().await;
    handle(
        &s,
        "AppointmentParticipationStatusRecheckRequestedEvent",
        json!({"appointment_id": s.appointment_id, "acting_user_id": ALICE}),
    )
    .await;

    let sent = s.sender.sent();
    assert_eq!(s.sender.recipients(), vec![ALICE, BOB, CAROL, DAVE]);
    assert_eq!(sent[0].1["type"], "PARTICIPATION_STATUS_RECHECK");
    assert_eq!(sent[0].1["participation_status"], "APPROVED");
    assert_eq!(sent[1].1["participation_status"], "PENDING");
}

// ---------------------------------------------------------------- messages, friendships, groups

#[tokio::test]
async fn message_sent_uses_title_and_body_without_type() {
    let s = setup().await;
    let message_id: i64 = sqlx::query_scalar(
        "INSERT INTO message (id, \"timestamp\", body, appointment_id, sender_oidcid)
         VALUES (nextval('message_seq'), NOW(), 'Bis gleich!', $1, $2) RETURNING id",
    )
    .bind(s.appointment_id)
    .bind(BOB)
    .fetch_one(s.db.pool())
    .await
    .unwrap();
    handle(&s, "MessageSentEvent", json!({"message_id": message_id})).await;

    // appointment_message defaults to ATTENDANT; bob is the sender, carol a GUEST
    assert_eq!(s.sender.recipients(), vec![ALICE, DAVE]);
    let payload = &s.sender.sent()[0].1;
    assert_eq!(payload["title"], "Test User schreibt zu Training");
    assert_eq!(payload["body"], "Bis gleich!");
    assert!(payload.get("type").is_none());
}

#[tokio::test]
async fn friendship_events_go_to_the_other_party() {
    let s = setup().await;
    handle(
        &s,
        "FriendshipRequestSentEvent",
        json!({"request_id": 7, "requester_id": ALICE, "addressee_id": BOB, "requester_name": "Alice A"}),
    )
    .await;
    handle(
        &s,
        "FriendshipAcceptedEvent",
        json!({"request_id": 7, "requester_id": ALICE, "addressee_id": BOB}),
    )
    .await;
    handle(
        &s,
        "FriendshipDeclinedEvent",
        json!({"request_id": 8, "requester_id": CAROL, "addressee_id": BOB}),
    )
    .await;
    handle(
        &s,
        "FriendshipRemovedEvent",
        json!({"acting_user_id": BOB, "friend_id": DAVE}),
    )
    .await;

    let sent = s.sender.sent();
    assert_eq!(
        sent.iter().map(|(u, _)| u.as_str()).collect::<Vec<_>>(),
        vec![ALICE, BOB, CAROL, DAVE]
    );
    let by_user: HashMap<_, _> = sent.into_iter().collect();
    assert_eq!(
        by_user[BOB],
        json!({"type": "NEW_FRIENDSHIP_REQUEST", "requester_id": ALICE, "requester_name": "Alice A", "request_id": 7})
    );
    assert_eq!(
        by_user[ALICE],
        json!({"type": "FRIENDSHIP_ACCEPTED", "addressee_id": BOB, "addressee_name": "Test User"})
    );
    assert_eq!(
        by_user[CAROL],
        json!({"type": "FRIENDSHIP_DECLINED", "addressee_id": BOB, "addressee_name": "Test User"})
    );
    assert_eq!(
        by_user[DAVE],
        json!({"type": "FRIENDSHIP_REMOVED", "acting_user_id": BOB, "acting_user_name": "Test User"})
    );
}

#[tokio::test]
async fn group_member_added_notifies_enabled_members_and_the_new_member() {
    let s = setup().await;
    let group_id = TestFixtures::new(s.db.pool().clone())
        .create_group(ALICE, "Jugend")
        .await
        .unwrap();
    for user in [ALICE, BOB, CAROL] {
        sqlx::query(
            "INSERT INTO group_member (id, group_id, user_oidcid)
             VALUES (nextval('group_member_seq'), $1, $2)",
        )
        .bind(group_id)
        .bind(user)
        .execute(s.db.pool())
        .await
        .unwrap();
    }
    // group_member_added defaults to DISABLED; only alice opts in
    sqlx::query("INSERT INTO settings (user_oidcid, group_member_added) VALUES ($1, 'ENABLED')")
        .bind(ALICE)
        .execute(s.db.pool())
        .await
        .unwrap();
    handle(
        &s,
        "GroupMemberAddedEvent",
        json!({"group_id": group_id, "user_id": CAROL}),
    )
    .await;

    let sent = s.sender.sent();
    assert_eq!(s.sender.recipients(), vec![ALICE, CAROL]);
    assert_eq!(sent[0].1["type"], "NEW_GROUP_MEMBER");
    assert_eq!(sent[1].1["type"], "ADDED_TO_GROUP");
    assert_eq!(sent[0].1["new_member"], "Test User");
    let group: Value = serde_json::from_str(sent[0].1["group"].as_str().unwrap()).unwrap();
    assert_eq!(group["id"], group_id);
    assert_eq!(group["name"], "Jugend");
    assert_eq!(group["members"].as_array().unwrap().len(), 3);
}

// ---------------------------------------------------------------- notification log

#[tokio::test]
async fn every_send_attempt_is_logged() {
    let s = setup().await;
    s.sender.respond(&endpoint(BOB), Ok(500));
    handle(
        &s,
        "AppointmentReminderEvent",
        json!({"appointment_id": s.appointment_id}),
    )
    .await;

    let rows: Vec<LogRow> = sqlx::query_as(
        "SELECT user_oidcid, notification_type, http_status_code, success, error_message
         FROM push_notification_log ORDER BY user_oidcid",
    )
    .fetch_all(s.db.pool())
    .await
    .unwrap();
    assert_eq!(rows.len(), 4);
    assert_eq!(
        rows[0],
        (
            ALICE.into(),
            Some("PARTICIPATION_REMINDER".into()),
            Some(201),
            true,
            None
        )
    );
    // a non-2xx answer is logged as failure; only 404/410 remove the subscription
    assert_eq!(
        rows[1],
        (
            BOB.into(),
            Some("PARTICIPATION_REMINDER".into()),
            Some(500),
            false,
            None
        )
    );
    let subscriptions: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM pushsubscription")
        .fetch_one(s.db.pool())
        .await
        .unwrap();
    assert_eq!(subscriptions, 4);
}

#[tokio::test]
async fn expired_or_failing_subscriptions_are_removed() {
    let s = setup().await;
    s.sender.respond(&endpoint(BOB), Ok(410));
    s.sender.respond(&endpoint(CAROL), Ok(404));
    s.sender
        .respond(&endpoint(DAVE), Err("connection refused".to_string()));
    handle(
        &s,
        "AppointmentReminderEvent",
        json!({"appointment_id": s.appointment_id}),
    )
    .await;

    let remaining: Vec<String> =
        sqlx::query_scalar("SELECT user_oidcid FROM pushsubscription ORDER BY user_oidcid")
            .fetch_all(s.db.pool())
            .await
            .unwrap();
    assert_eq!(remaining, vec![ALICE]);

    let dave: (Option<i32>, bool, Option<String>) = sqlx::query_as(
        "SELECT http_status_code, success, error_message FROM push_notification_log
         WHERE user_oidcid = $1",
    )
    .bind(DAVE)
    .fetch_one(s.db.pool())
    .await
    .unwrap();
    assert_eq!(dave, (None, false, Some("connection refused".to_string())));
}

#[tokio::test]
async fn without_a_push_sender_nothing_is_sent_or_logged() {
    let s = setup().await;
    let handler = PushEventHandler::new(
        s.db.pool().clone(),
        Arc::new(NotificationDispatcher::new(s.db.pool().clone(), None)),
    );
    handler
        .handle(&Event::new(
            "AppointmentReminderEvent",
            json!({"appointment_id": s.appointment_id}),
        ))
        .await
        .unwrap();
    let logged: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM push_notification_log")
        .fetch_one(s.db.pool())
        .await
        .unwrap();
    assert_eq!(logged, 0);
}

// ---------------------------------------------------------------- wiring

#[tokio::test]
async fn listener_reacts_to_events_fired_on_the_event_bus() {
    let s = setup().await;
    let bus = PostgresEventBus::new(s.db.pool().clone());
    PushNotificationEventListener::new(s.handler.clone())
        .subscribe_all(&bus)
        .await
        .expect("subscribed");

    // Subscriptions listen on background tasks; an event fired earlier would be missed
    for _ in 0..300 {
        let listening: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM pg_stat_activity
             WHERE datname = current_database() AND query ILIKE 'LISTEN%'",
        )
        .fetch_one(s.db.pool())
        .await
        .unwrap();
        if listening >= 15 {
            break;
        }
        tokio::time::sleep(Duration::from_millis(100)).await;
    }

    bus.fire(Event::new(
        "AppointmentReminderEvent",
        json!({"appointment_id": s.appointment_id}),
    ))
    .await
    .expect("fired");

    for _ in 0..100 {
        if s.sender.sent().len() == 4 {
            break;
        }
        tokio::time::sleep(Duration::from_millis(100)).await;
    }
    assert_eq!(s.sender.recipients(), vec![ALICE, BOB, CAROL, DAVE]);
}
