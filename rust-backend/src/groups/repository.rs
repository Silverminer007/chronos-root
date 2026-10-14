use crate::groups::models::{Friendship, Group, GroupMember};
use sqlx::PgPool;

// Columns of the Java-compatible tables, aliased to the field names of the models (ADR-2026-10-12-014).
// Ids come from the `<table>_seq` sequences, users are OIDC subjects (`*_oidcid`).
const GROUP_COLUMNS: &str = "id, groupname AS name, owner_oidcid AS owner_id";
const MEMBER_COLUMNS: &str = "id, group_id, user_oidcid AS user_id";
const FRIENDSHIP_COLUMNS: &str = "id, requester_oidcid AS requester_id, addressee_oidcid AS recipient_id, status, created_at, responded_at";

/// Repository for group operations
pub struct GroupRepository;

impl GroupRepository {
    /// Create a new group
    pub async fn create(pool: &PgPool, name: String, owner_id: &str) -> Result<Group, sqlx::Error> {
        sqlx::query_as::<_, Group>(&format!(
            "INSERT INTO groups (id, groupname, owner_oidcid)
             VALUES (nextval('groups_seq'), $1, $2)
             RETURNING {}",
            GROUP_COLUMNS
        ))
        .bind(name)
        .bind(owner_id)
        .fetch_one(pool)
        .await
    }

    /// Get a group by ID
    pub async fn get_by_id(pool: &PgPool, id: i64) -> Result<Option<Group>, sqlx::Error> {
        sqlx::query_as::<_, Group>(&format!(
            "SELECT {} FROM groups WHERE id = $1",
            GROUP_COLUMNS
        ))
        .bind(id)
        .fetch_optional(pool)
        .await
    }

    /// List all groups for a user (both owned and member of)
    pub async fn list_for_user(pool: &PgPool, user_id: &str) -> Result<Vec<Group>, sqlx::Error> {
        sqlx::query_as::<_, Group>(&format!(
            "SELECT {} FROM groups
             WHERE owner_oidcid = $1
                OR id IN (SELECT group_id FROM group_member WHERE user_oidcid = $1)
             ORDER BY id DESC",
            GROUP_COLUMNS
        ))
        .bind(user_id)
        .fetch_all(pool)
        .await
    }

    /// List all groups for a user (owned only)
    pub async fn list_by_owner(pool: &PgPool, owner_id: &str) -> Result<Vec<Group>, sqlx::Error> {
        sqlx::query_as::<_, Group>(&format!(
            "SELECT {} FROM groups WHERE owner_oidcid = $1 ORDER BY id DESC",
            GROUP_COLUMNS
        ))
        .bind(owner_id)
        .fetch_all(pool)
        .await
    }

    /// Update a group (only the name can change; without a name the group is returned as is)
    pub async fn update(
        pool: &PgPool,
        id: i64,
        name: Option<String>,
    ) -> Result<Option<Group>, sqlx::Error> {
        sqlx::query_as::<_, Group>(&format!(
            "UPDATE groups SET groupname = COALESCE($1, groupname) WHERE id = $2 RETURNING {}",
            GROUP_COLUMNS
        ))
        .bind(name)
        .bind(id)
        .fetch_optional(pool)
        .await
    }

    /// Delete a group (its members are removed by the ON DELETE CASCADE of `group_member`)
    pub async fn delete(pool: &PgPool, id: i64) -> Result<bool, sqlx::Error> {
        let result = sqlx::query("DELETE FROM groups WHERE id = $1")
            .bind(id)
            .execute(pool)
            .await?;

        Ok(result.rows_affected() > 0)
    }
}

/// Repository for group member operations
pub struct GroupMemberRepository;

impl GroupMemberRepository {
    /// Add a member to a group
    pub async fn add_member(
        pool: &PgPool,
        group_id: i64,
        user_id: &str,
    ) -> Result<GroupMember, sqlx::Error> {
        sqlx::query_as::<_, GroupMember>(&format!(
            "INSERT INTO group_member (id, group_id, user_oidcid)
             VALUES (nextval('group_member_seq'), $1, $2)
             RETURNING {}",
            MEMBER_COLUMNS
        ))
        .bind(group_id)
        .bind(user_id)
        .fetch_one(pool)
        .await
    }

    /// Remove a member from a group
    pub async fn remove_member(
        pool: &PgPool,
        group_id: i64,
        user_id: &str,
    ) -> Result<bool, sqlx::Error> {
        let result =
            sqlx::query("DELETE FROM group_member WHERE group_id = $1 AND user_oidcid = $2")
                .bind(group_id)
                .bind(user_id)
                .execute(pool)
                .await?;

        Ok(result.rows_affected() > 0)
    }

    /// Get all members of a group
    pub async fn get_members(
        pool: &PgPool,
        group_id: i64,
    ) -> Result<Vec<GroupMember>, sqlx::Error> {
        sqlx::query_as::<_, GroupMember>(&format!(
            "SELECT {} FROM group_member WHERE group_id = $1 ORDER BY id",
            MEMBER_COLUMNS
        ))
        .bind(group_id)
        .fetch_all(pool)
        .await
    }

    /// Check if a user is a member of a group
    pub async fn is_member(
        pool: &PgPool,
        group_id: i64,
        user_id: &str,
    ) -> Result<bool, sqlx::Error> {
        let result =
            sqlx::query("SELECT 1 FROM group_member WHERE group_id = $1 AND user_oidcid = $2")
                .bind(group_id)
                .bind(user_id)
                .fetch_optional(pool)
                .await?;

        Ok(result.is_some())
    }
}

/// Repository for friendship operations
pub struct FriendshipRepository;

impl FriendshipRepository {
    /// Create a friendship request
    pub async fn create_request(
        pool: &PgPool,
        requester_id: &str,
        recipient_id: &str,
    ) -> Result<Friendship, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(&format!(
            "INSERT INTO friendship_requests (id, requester_oidcid, addressee_oidcid, status, created_at)
             VALUES (nextval('friendship_requests_seq'), $1, $2, 'PENDING', NOW())
             RETURNING {}",
            FRIENDSHIP_COLUMNS
        ))
        .bind(requester_id)
        .bind(recipient_id)
        .fetch_one(pool)
        .await
    }

    /// Get a friendship by ID
    pub async fn get_by_id(pool: &PgPool, id: i64) -> Result<Option<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(&format!(
            "SELECT {} FROM friendship_requests WHERE id = $1",
            FRIENDSHIP_COLUMNS
        ))
        .bind(id)
        .fetch_optional(pool)
        .await
    }

    /// Accept a friendship request
    pub async fn accept(pool: &PgPool, id: i64) -> Result<Option<Friendship>, sqlx::Error> {
        Self::respond(pool, id, "ACCEPTED").await
    }

    /// Decline a friendship request
    pub async fn decline(pool: &PgPool, id: i64) -> Result<Option<Friendship>, sqlx::Error> {
        Self::respond(pool, id, "DECLINED").await
    }

    async fn respond(
        pool: &PgPool,
        id: i64,
        status: &str,
    ) -> Result<Option<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(&format!(
            "UPDATE friendship_requests SET status = $1, responded_at = NOW() WHERE id = $2 RETURNING {}",
            FRIENDSHIP_COLUMNS
        ))
        .bind(status)
        .bind(id)
        .fetch_optional(pool)
        .await
    }

    /// Remove a friendship
    pub async fn delete(pool: &PgPool, id: i64) -> Result<bool, sqlx::Error> {
        let result = sqlx::query("DELETE FROM friendship_requests WHERE id = $1")
            .bind(id)
            .execute(pool)
            .await?;

        Ok(result.rows_affected() > 0)
    }

    /// Get friendship between two users
    pub async fn get_friendship(
        pool: &PgPool,
        user1_id: &str,
        user2_id: &str,
    ) -> Result<Option<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(&format!(
            "SELECT {} FROM friendship_requests
             WHERE (requester_oidcid = $1 AND addressee_oidcid = $2)
                OR (requester_oidcid = $2 AND addressee_oidcid = $1)
             ORDER BY id
             LIMIT 1",
            FRIENDSHIP_COLUMNS
        ))
        .bind(user1_id)
        .bind(user2_id)
        .fetch_optional(pool)
        .await
    }

    /// List all friendships for a user (pending and accepted)
    pub async fn list_for_user(
        pool: &PgPool,
        user_id: &str,
    ) -> Result<Vec<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(&format!(
            "SELECT {} FROM friendship_requests
             WHERE requester_oidcid = $1 OR addressee_oidcid = $1
             ORDER BY created_at DESC, id DESC",
            FRIENDSHIP_COLUMNS
        ))
        .bind(user_id)
        .fetch_all(pool)
        .await
    }
}
