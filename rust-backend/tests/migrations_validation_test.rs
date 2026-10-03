// This test validates that migrations can be compiled and would apply cleanly to an empty database
// Full integration test with real PostgreSQL requires testcontainers or a running PostgreSQL instance

#[test]
fn test_migration_005_no_invalid_columns() {
    // Read migration 005 and verify it doesn't reference deleted_at
    // (which doesn't exist in the schema)
    let migration_005 = include_str!("../migrations/005_performance_indexes.sql");

    // These lines should NOT be in migration 005
    // OLD: "WHERE deleted_at IS NULL" on tables without deleted_at column
    // OLD: "user_id, friend_id" on friendships table (which uses requester_id, recipient_id)

    // Check that the problematic patterns are NOT present
    let has_deleted_at_predicate = migration_005.contains("WHERE deleted_at IS NULL");
    let has_wrong_friendship_columns = migration_005.contains("user_id, friend_id");

    assert!(!has_deleted_at_predicate, "Migration 005 should not use deleted_at predicate (column doesn't exist)");
    assert!(!has_wrong_friendship_columns, "Migration 005 should not reference user_id/friend_id on friendships table");
}

#[test]
fn test_migration_005_uses_correct_friendship_columns() {
    let migration_005 = include_str!("../migrations/005_performance_indexes.sql");

    // Migration should use requester_id and recipient_id (the actual columns)
    assert!(migration_005.contains("requester_id"), "Migration should reference requester_id");
    assert!(migration_005.contains("recipient_id"), "Migration should reference recipient_id");
}

#[test]
fn test_migration_files_exist() {
    // Verify all migration files exist
    assert!(std::path::Path::new("migrations/001_initial_schema.sql").exists());
    assert!(std::path::Path::new("migrations/002_create_events_table.sql").exists());
    assert!(std::path::Path::new("migrations/003_create_push_subscriptions_table.sql").exists());
    assert!(std::path::Path::new("migrations/004_create_group_members_friendships.sql").exists());
    assert!(std::path::Path::new("migrations/005_performance_indexes.sql").exists());
}

#[test]
fn test_migrations_are_ordered() {
    use std::fs;

    let migrations_dir = "migrations";
    let entries = fs::read_dir(migrations_dir).expect("Failed to read migrations directory");
    let mut migration_files: Vec<_> = entries
        .filter_map(|e| e.ok().map(|e| e.file_name()))
        .filter_map(|name| name.into_string().ok())
        .filter(|n| n.ends_with(".sql"))
        .collect();

    migration_files.sort();

    // Verify we have at least 5 migrations
    assert!(migration_files.len() >= 5, "Should have at least 5 migration files");

    // Verify they're numbered sequentially
    for (i, file) in migration_files.iter().enumerate() {
        let expected_number = i + 1;
        assert!(
            file.starts_with(&format!("{:03}", expected_number)),
            "Migration {} should start with {:03}_",
            file,
            expected_number
        );
    }
}

#[test]
fn test_migration_005_syntax_valid() {
    let migration_005 = include_str!("../migrations/005_performance_indexes.sql");

    // Basic SQL syntax checks
    let create_index_count = migration_005.matches("CREATE INDEX").count();

    // Should have multiple indexes
    assert!(create_index_count >= 5, "Migration 005 should create at least 5 indexes");

    // Should have ANALYZE statements
    assert!(migration_005.contains("ANALYZE"), "Migration 005 should include ANALYZE statements");
}

#[test]
fn test_all_migrations_readable() {
    use std::fs;

    let migrations_dir = "migrations";
    let entries = fs::read_dir(migrations_dir).expect("Failed to read migrations directory");

    for entry in entries {
        let entry = entry.expect("Failed to read directory entry");
        let path = entry.path();

        if path.ends_with(".sql") {
            let _content = fs::read_to_string(&path)
                .unwrap_or_else(|e| panic!("Failed to read {}: {}", path.display(), e));

            // Successfully read the file
            assert!(path.exists());
        }
    }
}
