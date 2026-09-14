# Query Optimization Best Practices

This document describes patterns for writing performant database queries in the Chronos Rust backend and avoiding common N+1 query anti-patterns.

## Table of Contents

1. [N+1 Query Pattern Detection](#n1-query-pattern-detection)
2. [JOIN Strategies](#join-strategies)
3. [Connection Pool Tuning](#connection-pool-tuning)
4. [Pagination](#pagination)
5. [Indexing Strategy](#indexing-strategy)
6. [Common Anti-Patterns](#common-anti-patterns)

## N+1 Query Pattern Detection

### What is an N+1 Query?

An N+1 query occurs when you execute one query to fetch N items, then execute N additional queries to fetch related data for each item.

**Example (BAD):**

```rust
// 1 query to fetch appointments
let appointments = sqlx::query_as::<_, Appointment>(
    "SELECT * FROM appointments WHERE creator_id = $1"
)
.bind(user_id)
.fetch_all(&pool)
.await?;

// N queries: one for each appointment's participants
let mut results = vec![];
for appt in appointments {
    let participants = sqlx::query_as::<_, Participant>(
        "SELECT * FROM appointment_participants WHERE appointment_id = $1"
    )
    .bind(appt.id)
    .fetch_all(&pool)
    .await?;
    
    results.push((appt, participants));
}
```

**This results in 1 + N queries (expensive!)**

### How to Detect N+1 Queries

1. **Enable SQL logging:**

```rust
// In tests or development
std::env::set_var("RUST_LOG", "sqlx=debug");

// Or in main.rs
tracing_subscriber::fmt()
    .with_env_filter("sqlx=debug")
    .init();
```

2. **Count queries during testing:**

Use the integration test framework with query logging enabled:

```bash
RUST_LOG=sqlx=debug cargo test --test '*' -- --ignored --nocapture | grep "SELECT\|INSERT\|UPDATE"
```

3. **Monitor in production:**

Add query counters to endpoints:

```rust
let query_start = std::time::Instant::now();
let appointments = sqlx::query_as(...)
    .fetch_all(&pool)
    .await?;
tracing::debug!("Query took: {:?}", query_start.elapsed());
```

## JOIN Strategies

### Strategy 1: SQL JOINs (Recommended for Relational Data)

**Use JOINs when fetching related entities in one query.**

```rust
// Good: Single query with JOIN
let results = sqlx::query!(
    r#"
    SELECT 
        a.id, a.title, a.start_time, a.end_time,
        p.id as participant_id, p.user_id, p.status
    FROM appointments a
    LEFT JOIN appointment_participants p ON a.id = p.appointment_id
    WHERE a.creator_id = $1
    "#,
    user_id
)
.fetch_all(&pool)
.await?;

// Transform into structured result
let mut appointments_map: HashMap<Uuid, Appointment> = HashMap::new();
for row in results {
    let appt = appointments_map.entry(row.id).or_insert_with(|| Appointment {
        id: row.id,
        title: row.title,
        start_time: row.start_time,
        end_time: row.end_time,
        participants: vec![],
    });

    if let Some(p_id) = row.participant_id {
        appt.participants.push(Participant {
            id: p_id,
            user_id: row.user_id,
            status: row.status,
        });
    }
}
```

### Strategy 2: Separate Queries with IN Clause (Batch Fetching)

**Use when you need related data but want to avoid large JOINs.**

```rust
// Good: Fetch appointments, then batch-fetch participants
let appointments = sqlx::query_as::<_, Appointment>(
    "SELECT * FROM appointments WHERE creator_id = $1"
)
.bind(user_id)
.fetch_all(&pool)
.await?;

let appointment_ids: Vec<Uuid> = appointments.iter().map(|a| a.id).collect();

let participants = sqlx::query_as::<_, (Uuid, Participant)>(
    "SELECT appointment_id, id, user_id, status 
     FROM appointment_participants 
     WHERE appointment_id = ANY($1)"
)
.bind(&appointment_ids)
.fetch_all(&pool)
.await?;

// Group participants by appointment
let participants_by_appt: HashMap<Uuid, Vec<Participant>> = 
    participants.iter()
        .fold(HashMap::new(), |mut map, (appt_id, p)| {
            map.entry(*appt_id).or_insert_with(Vec::new).push(p.clone());
            map
        });
```

### Strategy 3: Partial Data Fetching (Projection)

**Don't fetch columns you don't need.**

```rust
// Bad: Fetches all columns including large TEXT fields
let appointments = sqlx::query_as::<_, FullAppointment>(
    "SELECT * FROM appointments WHERE creator_id = $1"
)
.bind(user_id)
.fetch_all(&pool)
.await?;

// Good: Only fetch needed columns
let appointments = sqlx::query!(
    r#"SELECT id, title, start_time, end_time FROM appointments 
       WHERE creator_id = $1"#,
    user_id
)
.fetch_all(&pool)
.await?;
```

## Connection Pool Tuning

### Pool Configuration

Current optimal settings in `src/database/mod.rs`:

```rust
DatabaseConfig {
    max_connections: 16,        // Conservative for <50 MiB target
    min_idle: Some(2),          // Keep 2 idle connections ready
    connection_timeout: Duration::from_secs(5),
    max_lifetime: Duration::from_secs(1800), // 30 minutes
}
```

### Per-Request Connection Usage

**Best Practice: Use the connection from the pool efficiently**

```rust
// Good: Single connection per request
let appointments = sqlx::query_as(
    "SELECT * FROM appointments WHERE creator_id = $1"
)
.bind(user_id)
.fetch_all(&pool)
.await?;

// Bad: Multiple operations reusing same connection (unnecessary)
let conn = pool.acquire().await?;
let result1 = sqlx::query_as(...).fetch_all(&mut *conn).await?;
let result2 = sqlx::query_as(...).fetch_all(&mut *conn).await?;
drop(conn);
```

### Monitor Pool Health

```rust
// Log pool stats periodically (useful for debugging)
let pool_size = pool.size();
let active_conns = pool.num_active();
tracing::debug!(
    "Connection pool: {} active out of {} total",
    active_conns,
    pool_size
);

// Alert if pool is exhausted
if active_conns >= pool_size {
    tracing::warn!("Connection pool exhausted! Consider increasing max_connections");
}
```

## Pagination

### Cursor-Based Pagination (Recommended)

```rust
#[derive(Serialize)]
pub struct PaginatedResponse<T> {
    pub data: Vec<T>,
    pub next_cursor: Option<String>,
    pub has_more: bool,
}

pub async fn list_appointments_paginated(
    pool: &PgPool,
    user_id: Uuid,
    limit: i32,
    cursor: Option<String>,
) -> Result<PaginatedResponse<Appointment>> {
    let limit = std::cmp::min(limit, 100); // Cap at 100 per request

    // Parse cursor (timestamp of last item)
    let cursor_time = cursor
        .as_ref()
        .and_then(|c| chrono::DateTime::parse_from_rfc3339(c).ok())
        .map(|dt| dt.with_timezone(&chrono::Utc));

    let mut appointments = sqlx::query_as::<_, Appointment>(
        r#"
        SELECT * FROM appointments 
        WHERE creator_id = $1 
        AND (created_at < $2 OR $2::timestamptz IS NULL)
        ORDER BY created_at DESC
        LIMIT $3
        "#,
    )
    .bind(user_id)
    .bind(cursor_time)
    .bind(limit + 1)
    .fetch_all(pool)
    .await?;

    // Detect if there are more results
    let has_more = appointments.len() > limit as usize;
    if has_more {
        appointments.pop();
    }

    let next_cursor = appointments.last().map(|a| a.created_at.to_rfc3339());

    Ok(PaginatedResponse {
        data: appointments,
        next_cursor,
        has_more,
    })
}
```

## Indexing Strategy

### Key Indexes Added (Migration 005)

1. **Composite indexes for common filters:**
   - `idx_appointments_creator_start_time`: Appointments filtered by creator and ordered by time
   - `idx_participants_appointment_status`: Participants filtered by appointment and status

2. **Partial indexes for active data:**
   - `idx_active_appointments`: Only active (future) appointments

3. **Bidirectional lookups:**
   - `idx_friendships_user_friend` and `idx_friendships_friend_user`: Support lookups in both directions

### Index Design Rules

1. **Match your WHERE clauses:** Index columns used in WHERE conditions
2. **Order matters:** Place filter columns first, then sorting columns
3. **Use partial indexes:** Only index rows you actually query
4. **Avoid over-indexing:** Each index slows down INSERT/UPDATE/DELETE

### Verifying Index Usage

```sql
-- Check if indexes are being used
EXPLAIN ANALYZE 
SELECT * FROM appointments 
WHERE creator_id = $1 
ORDER BY start_time DESC;

-- Look for "Index" in the plan output
-- If "Seq Scan" appears, the query may need a better index
```

## Common Anti-Patterns

### Anti-Pattern 1: Unbounded Result Sets

```rust
// Bad: No limit
let appointments = sqlx::query_as::<_, Appointment>(
    "SELECT * FROM appointments"
)
.fetch_all(&pool)
.await?; // Could fetch millions of rows!

// Good: Always paginate
const PAGE_SIZE: i32 = 50;
let appointments = sqlx::query_as::<_, Appointment>(
    "SELECT * FROM appointments LIMIT $1"
)
.bind(PAGE_SIZE)
.fetch_all(&pool)
.await?;
```

### Anti-Pattern 2: SELECT *

```rust
// Bad: Fetches all columns including large TEXT fields
let appointments = sqlx::query_as::<_, FullAppointment>(
    "SELECT * FROM appointments"
)
.fetch_all(&pool)
.await?;

// Good: Explicit column selection
let appointments = sqlx::query!(
    "SELECT id, title, start_time, end_time FROM appointments"
)
.fetch_all(&pool)
.await?;
```

### Anti-Pattern 3: Correlated Subqueries

```rust
// Bad: Correlated subquery runs for each row
let results = sqlx::query!(
    r#"
    SELECT a.id, a.title,
           (SELECT COUNT(*) FROM appointment_participants 
            WHERE appointment_id = a.id) as participant_count
    FROM appointments a
    WHERE creator_id = $1
    "#,
    user_id
)
.fetch_all(&pool)
.await?;

// Good: Use JOIN with aggregation
let results = sqlx::query!(
    r#"
    SELECT a.id, a.title, COUNT(p.id) as participant_count
    FROM appointments a
    LEFT JOIN appointment_participants p ON a.id = p.appointment_id
    WHERE a.creator_id = $1
    GROUP BY a.id
    "#,
    user_id
)
.fetch_all(&pool)
.await?;
```

### Anti-Pattern 4: Transactions Holding Locks

```rust
// Bad: Long-running operation in transaction
let mut tx = pool.begin().await?;
let appointments = sqlx::query_as(...)
    .fetch_all(&mut *tx)
    .await?;
// ... do some async work here (database is locked!)
let result = process_appointments(appointments).await?;
tx.commit().await?;

// Good: Keep transactions short, do async work after
let appointments = sqlx::query_as(...)
    .fetch_all(&pool)
    .await?;
let result = process_appointments(appointments).await?;

let mut tx = pool.begin().await?;
save_result(&mut *tx, result).await?;
tx.commit().await?;
```

## Performance Monitoring

### Enable Query Logging

```rust
// In main.rs
std::env::set_var("RUST_LOG", "sqlx=info");
tracing_subscriber::fmt()
    .with_env_filter("sqlx=info")
    .init();
```

### Sample Output

```
2024-09-14T10:15:30Z DEBUG sqlx::query: sqlx::postgres: query=SELECT * FROM appointments WHERE creator_id = $1; execution time=2.5ms
2024-09-14T10:15:31Z DEBUG sqlx::query: sqlx::postgres: query=SELECT * FROM appointment_participants WHERE appointment_id = ANY($1); execution time=1.2ms
```

## References

- [SQLx Documentation](https://github.com/launchbadge/sqlx)
- [PostgreSQL Query Performance](https://www.postgresql.org/docs/current/sql-explain.html)
- [Database Indexing Strategy](https://use-the-index-luke.com/)
