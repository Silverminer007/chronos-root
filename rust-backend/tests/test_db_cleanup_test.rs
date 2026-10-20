//! `TestDb` must not leave databases behind, and the janitor must only remove stale test databases.

use chronos_date_api::test_utils::janitor::{drop_stale_databases, STALE_AFTER};
use chronos_date_api::test_utils::TestDb;
use sqlx::PgPool;

async fn admin_pool() -> PgPool {
    let url = std::env::var("TEST_DATABASE_URL")
        .or_else(|_| std::env::var("DATABASE_URL"))
        .unwrap_or_else(|_| "postgres://postgres:postgres@localhost:5432/postgres".to_string());
    let mut url = url::Url::parse(&url).unwrap();
    url.set_path("/postgres");
    PgPool::connect(url.as_str()).await.unwrap()
}

async fn exists(admin: &PgPool, name: &str) -> bool {
    sqlx::query_scalar("SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = $1)")
        .bind(name)
        .fetch_one(admin)
        .await
        .unwrap()
}

#[tokio::test(flavor = "multi_thread")]
async fn database_is_dropped_with_the_guard() {
    let admin = admin_pool().await;
    let db = TestDb::new().await.unwrap();
    let name = db.name().to_string();
    assert!(exists(&admin, &name).await);

    drop(db);

    assert!(!exists(&admin, &name).await);
}

#[tokio::test(flavor = "multi_thread")]
async fn database_is_dropped_when_the_test_panics() {
    let admin = admin_pool().await;
    let (tx, rx) = std::sync::mpsc::channel();

    // Own thread and runtime: the panic unwinds through the guard like in a failing test
    let panicking = std::thread::spawn(move || {
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let db = TestDb::new().await.unwrap();
            tx.send(db.name().to_string()).unwrap();
            panic!("simulated test failure");
        })
    });

    assert!(panicking.join().is_err());
    assert!(!exists(&admin, &rx.recv().unwrap()).await);
}

#[tokio::test(flavor = "multi_thread")]
async fn janitor_drops_only_stale_unconnected_databases_with_its_prefix() {
    let admin = admin_pool().await;
    // A private prefix keeps this test away from databases of other runs
    let prefix = format!("janitor_probe_{}_", uuid::Uuid::new_v4().simple());
    let now = 1_700_000_000_u64;
    let old = now - STALE_AFTER.as_secs() - 1;
    let recent = now - 60;
    let stale = format!("{prefix}{old}_a");
    let fresh = format!("{prefix}{recent}_b");
    let connected = format!("{prefix}{old}_c");
    let untimed = format!("{prefix}legacy");
    let foreign = format!("other_{}", uuid::Uuid::new_v4().simple());
    for name in [&stale, &fresh, &connected, &untimed, &foreign] {
        sqlx::query(&format!("CREATE DATABASE \"{name}\""))
            .execute(&admin)
            .await
            .unwrap();
    }
    let mut url =
        url::Url::parse(&std::env::var("TEST_DATABASE_URL").unwrap_or_else(|_| {
            "postgres://postgres:postgres@localhost:5432/postgres".to_string()
        }))
        .unwrap();
    url.set_path(&format!("/{connected}"));
    let session = PgPool::connect(url.as_str()).await.unwrap();

    let dropped = drop_stale_databases(&admin, &prefix, now, STALE_AFTER, 10)
        .await
        .unwrap();

    assert_eq!(dropped, vec![stale.clone()]);
    assert!(!exists(&admin, &stale).await);
    for kept in [&fresh, &connected, &untimed, &foreign] {
        assert!(exists(&admin, kept).await, "{kept} must survive");
    }

    session.close().await;
    for name in [&fresh, &connected, &untimed, &foreign] {
        sqlx::query(&format!("DROP DATABASE \"{name}\""))
            .execute(&admin)
            .await
            .unwrap();
    }
}
