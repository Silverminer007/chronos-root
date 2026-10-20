use crate::groups::models::{Friendship, FriendshipStatus, Group, GroupMember};
use sqlx::PgExecutor;
use std::collections::HashSet;

// Columns of the Java-compatible tables, aliased to the field names of the models (ADR-2026-10-12-014).
// Ids come from the `<table>_seq` sequences, users are OIDC subjects (`*_oidcid`).
// Every function takes a pool or a transaction as executor.
const GROUP_COLUMNS: &str = "id, groupname AS name, owner_oidcid AS owner_id";
const MEMBER_COLUMNS: &str = "id, group_id, user_oidcid AS user_id";
const FRIENDSHIP_COLUMNS: &str = "id, requester_oidcid AS requester_id, addressee_oidcid AS addressee_id, status, created_at, responded_at";

/// Repository for group operations
pub struct GroupRepository;

impl GroupRepository {
    /// Create a new group
    pub async fn create<'e>(
        db: impl PgExecutor<'e>,
        name: &str,
        owner_id: &str,
    ) -> Result<Group, sqlx::Error> {
        sqlx::query_as::<_, Group>(&format!(
            "INSERT INTO groups (id, groupname, owner_oidcid)
             VALUES (nextval('groups_seq'), $1, $2)
             RETURNING {GROUP_COLUMNS}"
        ))
        .bind(name)
        .bind(owner_id)
        .fetch_one(db)
        .await
    }

    /// Get a group by ID
    pub async fn find_by_id<'e>(
        db: impl PgExecutor<'e>,
        id: i64,
    ) -> Result<Option<Group>, sqlx::Error> {
        sqlx::query_as::<_, Group>(&format!("SELECT {GROUP_COLUMNS} FROM groups WHERE id = $1"))
            .bind(id)
            .fetch_optional(db)
            .await
    }

    /// Groups the user is a member of, optionally filtered by a case-insensitive name fragment
    /// (like Java, `%` and `_` in the fragment keep their LIKE meaning)
    pub async fn search_for_member<'e>(
        db: impl PgExecutor<'e>,
        user_id: &str,
        search: Option<&str>,
    ) -> Result<Vec<Group>, sqlx::Error> {
        sqlx::query_as::<_, Group>(&format!(
            "SELECT {GROUP_COLUMNS} FROM groups
             WHERE id IN (SELECT group_id FROM group_member WHERE user_oidcid = $1)
               AND ($2::text IS NULL OR lower(groupname) LIKE lower('%' || $2 || '%'))
             ORDER BY id"
        ))
        .bind(user_id)
        .bind(search)
        .fetch_all(db)
        .await
    }

    /// True if the user owns the group
    pub async fn is_owner<'e>(
        db: impl PgExecutor<'e>,
        group_id: i64,
        user_id: &str,
    ) -> Result<bool, sqlx::Error> {
        sqlx::query_scalar::<_, bool>(
            "SELECT EXISTS (SELECT 1 FROM groups WHERE id = $1 AND owner_oidcid = $2)",
        )
        .bind(group_id)
        .bind(user_id)
        .fetch_one(db)
        .await
    }

    /// Rename a group
    pub async fn rename<'e>(
        db: impl PgExecutor<'e>,
        id: i64,
        name: &str,
    ) -> Result<(), sqlx::Error> {
        sqlx::query("UPDATE groups SET groupname = $1 WHERE id = $2")
            .bind(name)
            .bind(id)
            .execute(db)
            .await?;
        Ok(())
    }

    /// Delete a group (its members are removed by the ON DELETE CASCADE of `group_member`)
    pub async fn delete<'e>(db: impl PgExecutor<'e>, id: i64) -> Result<bool, sqlx::Error> {
        let result = sqlx::query("DELETE FROM groups WHERE id = $1")
            .bind(id)
            .execute(db)
            .await?;

        Ok(result.rows_affected() > 0)
    }
}

/// Repository for group member operations
pub struct GroupMemberRepository;

impl GroupMemberRepository {
    /// Add a member to a group
    pub async fn add<'e>(
        db: impl PgExecutor<'e>,
        group_id: i64,
        user_id: &str,
    ) -> Result<(), sqlx::Error> {
        sqlx::query(
            "INSERT INTO group_member (id, group_id, user_oidcid)
             VALUES (nextval('group_member_seq'), $1, $2)",
        )
        .bind(group_id)
        .bind(user_id)
        .execute(db)
        .await?;
        Ok(())
    }

    /// Remove a member from a group; false if the user was no member
    pub async fn remove<'e>(
        db: impl PgExecutor<'e>,
        group_id: i64,
        user_id: &str,
    ) -> Result<bool, sqlx::Error> {
        let result =
            sqlx::query("DELETE FROM group_member WHERE group_id = $1 AND user_oidcid = $2")
                .bind(group_id)
                .bind(user_id)
                .execute(db)
                .await?;

        Ok(result.rows_affected() > 0)
    }

    /// Members of the given groups, oldest membership first
    pub async fn list_for_groups<'e>(
        db: impl PgExecutor<'e>,
        group_ids: &[i64],
    ) -> Result<Vec<GroupMember>, sqlx::Error> {
        sqlx::query_as::<_, GroupMember>(&format!(
            "SELECT {MEMBER_COLUMNS} FROM group_member WHERE group_id = ANY($1) ORDER BY id"
        ))
        .bind(group_ids)
        .fetch_all(db)
        .await
    }

    /// Check if a user is a member of a group
    pub async fn is_member<'e>(
        db: impl PgExecutor<'e>,
        group_id: i64,
        user_id: &str,
    ) -> Result<bool, sqlx::Error> {
        sqlx::query_scalar::<_, bool>(
            "SELECT EXISTS (SELECT 1 FROM group_member WHERE group_id = $1 AND user_oidcid = $2)",
        )
        .bind(group_id)
        .bind(user_id)
        .fetch_one(db)
        .await
    }
}

/// Repository for friendship operations
pub struct FriendshipRepository;

impl FriendshipRepository {
    /// Create a pending friendship request
    pub async fn create_request<'e>(
        db: impl PgExecutor<'e>,
        requester_id: &str,
        addressee_id: &str,
    ) -> Result<Friendship, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(&format!(
            "INSERT INTO friendship_requests (id, requester_oidcid, addressee_oidcid, status, created_at)
             VALUES (nextval('friendship_requests_seq'), $1, $2, '{pending}', NOW())
             RETURNING {FRIENDSHIP_COLUMNS}",
            pending = FriendshipStatus::Pending.as_str()
        ))
        .bind(requester_id)
        .bind(addressee_id)
        .fetch_one(db)
        .await
    }

    /// Get a friendship request by ID
    pub async fn find_by_id<'e>(
        db: impl PgExecutor<'e>,
        id: i64,
    ) -> Result<Option<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(&format!(
            "SELECT {FRIENDSHIP_COLUMNS} FROM friendship_requests WHERE id = $1"
        ))
        .bind(id)
        .fetch_optional(db)
        .await
    }

    /// Answer a request: set its status and `responded_at`
    pub async fn respond<'e>(
        db: impl PgExecutor<'e>,
        id: i64,
        status: FriendshipStatus,
    ) -> Result<(), sqlx::Error> {
        sqlx::query(
            "UPDATE friendship_requests SET status = $1, responded_at = NOW() WHERE id = $2",
        )
        .bind(status.as_str())
        .bind(id)
        .execute(db)
        .await?;
        Ok(())
    }

    /// Delete a friendship request
    pub async fn delete<'e>(db: impl PgExecutor<'e>, id: i64) -> Result<bool, sqlx::Error> {
        let result = sqlx::query("DELETE FROM friendship_requests WHERE id = $1")
            .bind(id)
            .execute(db)
            .await?;

        Ok(result.rows_affected() > 0)
    }

    /// The request between two users, in either direction, with any status
    pub async fn find_request<'e>(
        db: impl PgExecutor<'e>,
        user1_id: &str,
        user2_id: &str,
    ) -> Result<Option<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(&format!(
            "SELECT {FRIENDSHIP_COLUMNS} FROM friendship_requests
             WHERE (requester_oidcid = $1 AND addressee_oidcid = $2)
                OR (requester_oidcid = $2 AND addressee_oidcid = $1)
             ORDER BY id
             LIMIT 1"
        ))
        .bind(user1_id)
        .bind(user2_id)
        .fetch_optional(db)
        .await
    }

    /// The accepted request between two users, in either direction
    pub async fn find_friendship<'e>(
        db: impl PgExecutor<'e>,
        user1_id: &str,
        user2_id: &str,
    ) -> Result<Option<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(&format!(
            "SELECT {FRIENDSHIP_COLUMNS} FROM friendship_requests
             WHERE status = '{accepted}'
               AND ((requester_oidcid = $1 AND addressee_oidcid = $2)
                 OR (requester_oidcid = $2 AND addressee_oidcid = $1))
             ORDER BY id
             LIMIT 1",
            accepted = FriendshipStatus::Accepted.as_str()
        ))
        .bind(user1_id)
        .bind(user2_id)
        .fetch_optional(db)
        .await
    }

    /// True if the two users have an accepted request
    pub async fn are_friends<'e>(
        db: impl PgExecutor<'e>,
        user1_id: &str,
        user2_id: &str,
    ) -> Result<bool, sqlx::Error> {
        Ok(Self::find_friendship(db, user1_id, user2_id)
            .await?
            .is_some())
    }

    /// Accepted requests of a user, oldest first
    pub async fn list_friendships<'e>(
        db: impl PgExecutor<'e>,
        user_id: &str,
    ) -> Result<Vec<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(&format!(
            "SELECT {FRIENDSHIP_COLUMNS} FROM friendship_requests
             WHERE status = '{accepted}' AND (requester_oidcid = $1 OR addressee_oidcid = $1)
             ORDER BY id",
            accepted = FriendshipStatus::Accepted.as_str()
        ))
        .bind(user_id)
        .fetch_all(db)
        .await
    }

    /// Ids of all friends of a user
    pub async fn friend_ids<'e>(
        db: impl PgExecutor<'e>,
        user_id: &str,
    ) -> Result<HashSet<String>, sqlx::Error> {
        Ok(Self::list_friendships(db, user_id)
            .await?
            .iter()
            .map(|f| f.other_party(user_id).to_string())
            .collect())
    }

    /// Pending requests addressed to the user
    pub async fn list_incoming<'e>(
        db: impl PgExecutor<'e>,
        user_id: &str,
    ) -> Result<Vec<Friendship>, sqlx::Error> {
        Self::list_pending(db, "addressee_oidcid", user_id).await
    }

    /// Pending requests sent by the user
    pub async fn list_outgoing<'e>(
        db: impl PgExecutor<'e>,
        user_id: &str,
    ) -> Result<Vec<Friendship>, sqlx::Error> {
        Self::list_pending(db, "requester_oidcid", user_id).await
    }

    async fn list_pending<'e>(
        db: impl PgExecutor<'e>,
        user_column: &str,
        user_id: &str,
    ) -> Result<Vec<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(&format!(
            "SELECT {FRIENDSHIP_COLUMNS} FROM friendship_requests
             WHERE {user_column} = $1 AND status = '{pending}'
             ORDER BY id",
            pending = FriendshipStatus::Pending.as_str()
        ))
        .bind(user_id)
        .fetch_all(db)
        .await
    }
}
