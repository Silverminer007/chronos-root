/// Integration tests for the reminder scheduler and its leader election (ADR-2026-10-20-022).
/// Both need a PostgreSQL server (TEST_DATABASE_URL); every test creates its own database.
/// Time is a `FakeClock`, the push service a fake, so the notifications can be asserted exactly.
use async_trait::async_trait;
use chrono::{DateTime, Duration as ChronoDuration, Utc};
use chronos_date_api::appointments::repository::AppointmentRepository;
use chronos_date_api::event_bus::postgres::PostgresEventBus;
use chronos_date_api::event_bus::{Event, EventPublisher, EventSubscriber};
use chronos_date_api::push_notifications::models::PushSubscription;
use chronos_date_api::push_notifications::services::{NotificationDispatcher, PushSender};
use chronos_date_api::push_notifications::{PushEventHandler, PushNotificationEventListener};
use chronos_date_api::reminders::clock::{Clock, FakeClock};
use chronos_date_api::reminders::leader::{LeaderElection, SCHEDULER_LOCK_KEY};
use chronos_date_api::reminders::rules::default_rules;
use chronos_date_api::reminders::scheduler::ReminderScheduler;
use chronos_date_api::reminders::services::{BusReminderEventPort, ReminderRuleEngine};
use chronos_date_api::test_utils::{AppointmentFixture, TestDb, TestFixtures};
use serde_json::Value;
use sqlx::PgPool;
use std::sync::{Arc, Mutex};
use std::time::Duration;

const ALICE: &str = "alice";
const BOB: &str = "bob";

fn at(date: &str) -> DateTime<Utc> {
    DateTime::parse_from_rfc3339(date)
        .unwrap()
        .with_timezone(&Utc)
}

#[derive(Default)]
struct FakePushSender {
    sent: Mutex<Vec<(String, Value)>>,
}

impl FakePushSender {
    fn sent(&self) -> Vec<(String, Value)> {
        self.sent.lock().unwrap().clone()
    }
}

#[async_trait]
impl PushSender for FakePushSender {
    async fn send(&self, subscription: &PushSubscription, payload: &str) -> Result<u16, String> {
        self.sent.lock().unwrap().push((
            subscription.endpoint.clone(),
            serde_json::from_str(payload).expect("payload is JSON"),
        ));
        Ok(201)
    }
}

/// A second pool on the database of `pool`, i.e. a second backend replica
async fn second_pool(pool: &PgPool) -> PgPool {
    sqlx::postgres::PgPoolOptions::new()
        .max_connections(5)
        .connect_with((*pool.connect_options()).clone())
        .await
        .expect("second pool")
}

fn scheduler(pool: &PgPool, election: LeaderElection, clock: Arc<dyn Clock>) -> ReminderScheduler {
    let bus: Arc<dyn EventPublisher> = Arc::new(PostgresEventBus::new(pool.clone()));
    ReminderScheduler::new(
        Arc::new(AppointmentRepository::new(pool.clone())),
        ReminderRuleEngine::new(default_rules(), Arc::new(BusReminderEventPort::new(bus))),
        Arc::new(election),
        clock,
    )
}

// ---------------------------------------------------------------- leader election

#[tokio::test]
#[ignore] // Requires a PostgreSQL server (TEST_DATABASE_URL)
async fn exactly_one_of_two_instances_is_leader() {
    let db = TestDb::new().await.expect("test database");
    let a = LeaderElection::new(db.pool().clone());
    let b = LeaderElection::new(second_pool(db.pool()).await);

    assert!(a.is_leader().await);
    assert!(!b.is_leader().await);
    // stable: asking again changes nothing
    assert!(a.is_leader().await);
    assert!(!b.is_leader().await);

    // the follower takes over as soon as the leader resigns
    a.resign().await;
    assert!(b.is_leader().await);
    assert!(!a.is_leader().await);
}

#[tokio::test]
#[ignore] // Requires a PostgreSQL server (TEST_DATABASE_URL)
async fn follower_takes_over_when_the_leader_connection_dies() {
    let db = TestDb::new().await.expect("test database");
    let a = LeaderElection::new(db.pool().clone());
    let b = LeaderElection::new(second_pool(db.pool()).await);
    assert!(a.is_leader().await);
    assert!(!b.is_leader().await);

    // simulate a crashed leader: the server ends the session that holds the lock
    sqlx::query(
        "SELECT pg_terminate_backend(pid) FROM pg_locks
         WHERE locktype = 'advisory' AND granted AND objid = $1::bigint::oid",
    )
    .bind(SCHEDULER_LOCK_KEY & 0xFFFF_FFFF)
    .execute(db.pool())
    .await
    .unwrap();

    // the dead leader notices the loss, the other instance wins the lock
    assert!(b.is_leader().await);
    assert!(!a.is_leader().await);
}

#[tokio::test]
#[ignore] // Requires a PostgreSQL server (TEST_DATABASE_URL)
async fn only_one_of_two_schedulers_runs() {
    let db = TestDb::new().await.expect("test database");
    let clock: Arc<dyn Clock> = Arc::new(FakeClock::new(at("2030-01-01T00:00:00Z")));
    let first = scheduler(
        db.pool(),
        LeaderElection::new(db.pool().clone()),
        clock.clone(),
    );
    let other_pool = second_pool(db.pool()).await;
    let second = scheduler(&other_pool, LeaderElection::new(other_pool.clone()), clock);

    let (r1, r2) = tokio::join!(first.run_once(), second.run_once());
    let ran: Vec<_> = [r1.unwrap(), r2.unwrap()]
        .into_iter()
        .filter(Option::is_some)
        .collect();
    assert_eq!(ran.len(), 1, "exactly one scheduler instance may run");
}

#[tokio::test]
#[ignore] // Requires a PostgreSQL server (TEST_DATABASE_URL)
async fn disabled_election_runs_every_instance() {
    let db = TestDb::new().await.expect("test database");
    let clock: Arc<dyn Clock> = Arc::new(FakeClock::new(at("2030-01-01T00:00:00Z")));
    let first = scheduler(db.pool(), LeaderElection::disabled(), clock.clone());
    let second = scheduler(db.pool(), LeaderElection::disabled(), clock);

    assert!(first.run_once().await.unwrap().is_some());
    assert!(second.run_once().await.unwrap().is_some());
}

// ---------------------------------------------------------------- reminders end to end

struct Setup {
    db: TestDb,
    /// The dispatcher listens only as long as the bus lives
    _bus: PostgresEventBus,
    sender: Arc<FakePushSender>,
    clock: Arc<FakeClock>,
    scheduler: ReminderScheduler,
    fixtures: TestFixtures,
}

/// alice (RESPONSIBLE, APPROVED) and bob (ATTENDANT, PENDING) own a push subscription each.
/// The push listener is subscribed to a real `PostgresEventBus`.
async fn setup(now: &str) -> Setup {
    let db = TestDb::new().await.expect("test database");
    let fixtures = TestFixtures::new(db.pool().clone());
    for user in [ALICE, BOB] {
        fixtures
            .create_user(user, &format!("{user}@example.org"))
            .await
            .unwrap();
        sqlx::query(
            "INSERT INTO pushsubscription (id, user_oidcid, endpoint, p256dh, auth)
             VALUES (nextval('pushsubscription_seq'), $1, $2, 'p256dh', 'auth')",
        )
        .bind(user)
        .bind(format!("https://push.test/{user}"))
        .execute(db.pool())
        .await
        .unwrap();
    }

    let sender = Arc::new(FakePushSender::default());
    let handler = Arc::new(PushEventHandler::new(
        db.pool().clone(),
        Arc::new(NotificationDispatcher::new(
            db.pool().clone(),
            Some(sender.clone() as Arc<dyn PushSender>),
        )),
    ));
    let bus = PostgresEventBus::new(db.pool().clone());
    PushNotificationEventListener::new(handler)
        .subscribe_all(&bus)
        .await
        .expect("subscribed");
    // Subscriptions are applied by one dispatcher task in order. Once a probe subscribed after the
    // push listener receives its event, every earlier channel is listened on too; an event fired
    // before that would be missed.
    let (probe_tx, mut probe_rx) = tokio::sync::mpsc::unbounded_channel();
    bus.subscribe("subscription_probe".to_string(), move |_| {
        let _ = probe_tx.send(());
    })
    .await
    .expect("probe subscribed");
    tokio::time::timeout(Duration::from_secs(30), async {
        loop {
            bus.fire(Event::new("subscription_probe", serde_json::json!({})))
                .await
                .expect("probe fired");
            if let Ok(Some(())) =
                tokio::time::timeout(Duration::from_millis(300), probe_rx.recv()).await
            {
                break;
            }
        }
    })
    .await
    .expect("dispatcher listening");

    let clock = Arc::new(FakeClock::new(at(now)));
    let scheduler = scheduler(db.pool(), LeaderElection::disabled(), clock.clone());
    Setup {
        db,
        _bus: bus,
        sender,
        clock,
        scheduler,
        fixtures,
    }
}

impl Setup {
    /// Appointment of alice with bob as pending attendant
    async fn appointment(&self, start: &str, hours: i64) -> i64 {
        let start = at(start);
        let id = self
            .fixtures
            .create_appointment(
                ALICE,
                &AppointmentFixture::new()
                    .with_title("Training")
                    .with_times(start, start + ChronoDuration::hours(hours)),
            )
            .await
            .unwrap();
        // The fixture creator starts as PENDING (as in the API); alice has already accepted
        sqlx::query(
            "UPDATE appointment_participation SET status = 'APPROVED'
             WHERE appointment_id = $1 AND user_oidcid = $2",
        )
        .bind(id)
        .bind(ALICE)
        .execute(self.db.pool())
        .await
        .unwrap();
        self.fixtures
            .add_participant(id, BOB, "PENDING")
            .await
            .unwrap();
        id
    }

    /// Waits for `count` notifications; then lets stragglers arrive to catch extras
    async fn wait_for_notifications(&self, count: usize) -> Vec<(String, Value)> {
        for _ in 0..100 {
            if self.sender.sent().len() >= count {
                break;
            }
            tokio::time::sleep(Duration::from_millis(100)).await;
        }
        tokio::time::sleep(Duration::from_millis(300)).await;
        self.sender.sent()
    }
}

// Monday 2030-01-07 10:00, 4 hours: ShortWeekdayRSVPRule, triggers 7/4/2/1 days before
const MONDAY: &str = "2030-01-07T10:00:00Z";

#[tokio::test]
#[ignore] // Requires a PostgreSQL server (TEST_DATABASE_URL)
async fn due_appointment_notifies_pending_participants() {
    let s = setup("2030-01-03T10:05:00Z").await; // 4 days before + 5 minutes
    s.appointment(MONDAY, 4).await;

    assert_eq!(s.scheduler.run_once().await.unwrap(), Some(1));

    let sent = s.wait_for_notifications(1).await;
    assert_eq!(sent.len(), 1, "only the pending participant is reminded");
    assert_eq!(sent[0].0, "https://push.test/bob");
    assert_eq!(sent[0].1["type"], "PARTICIPATION_STATUS_PENDING");
    let log: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM push_notification_log WHERE success")
        .fetch_one(s.db.pool())
        .await
        .unwrap();
    assert_eq!(log, 1);
}

#[tokio::test]
#[ignore] // Requires a PostgreSQL server (TEST_DATABASE_URL)
async fn advancing_the_clock_moves_through_the_trigger_times() {
    let s = setup("2030-01-02T09:00:00Z").await; // between the -7d and the -4d trigger
    s.appointment(MONDAY, 4).await;

    assert_eq!(s.scheduler.run_once().await.unwrap(), Some(0));

    s.clock.set(at("2030-01-03T10:00:00Z")); // -4 days
    assert_eq!(s.scheduler.run_once().await.unwrap(), Some(1));

    s.clock.advance(ChronoDuration::hours(1)); // window of -4 days is over
    assert_eq!(s.scheduler.run_once().await.unwrap(), Some(0));

    s.clock.set(at("2030-01-06T10:15:00Z")); // -1 day + 15 minutes: still inside the window
    assert_eq!(s.scheduler.run_once().await.unwrap(), Some(1));

    assert_eq!(s.wait_for_notifications(2).await.len(), 2);
}

#[tokio::test]
#[ignore] // Requires a PostgreSQL server (TEST_DATABASE_URL)
async fn cancelled_started_and_far_away_appointments_are_skipped() {
    let s = setup("2030-01-03T10:00:00Z").await;
    // all three would be due, if they were considered
    let cancelled = s.appointment(MONDAY, 4).await;
    sqlx::query("UPDATE appointment SET status = 1 WHERE id = $1")
        .bind(cancelled)
        .execute(s.db.pool())
        .await
        .unwrap();
    // started before "now" (a long appointment has a trigger every day)
    s.appointment("2030-01-03T09:00:00Z", 48).await;
    // more than 20 weeks ahead: the long rule fires 16 weeks before the start = now
    s.appointment("2030-05-23T10:00:00Z", 48).await;

    assert_eq!(s.scheduler.run_once().await.unwrap(), Some(0));
    assert!(s.wait_for_notifications(1).await.is_empty());
}

#[tokio::test]
#[ignore] // Requires a PostgreSQL server (TEST_DATABASE_URL)
async fn long_appointment_is_reminded_daily_in_the_last_two_weeks() {
    let s = setup("2030-03-26T12:00:00Z").await; // 13 days before the start, 12:00
    s.appointment("2030-04-08T10:00:00Z", 72).await;
    // daily triggers sit at 10:00 of each of the last 14 days
    assert_eq!(s.scheduler.run_once().await.unwrap(), Some(0));
    s.clock.set(at("2030-03-26T10:10:00Z"));
    assert_eq!(s.scheduler.run_once().await.unwrap(), Some(1));
}
