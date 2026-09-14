use crate::groups::models::{Friendship, Group, GroupMember};
use sqlx::PgPool;
use uuid::Uuid;

/// Repository for group operations
pub struct GroupRepository;

impl GroupRepository {
    /// Create a new group
    pub async fn create(
        pool: &PgPool,
        id: Uuid,
        name: String,
        description: Option<String>,
        owner_id: Uuid,
    ) -> Result<Group, sqlx::Error> {
        sqlx::query_as::<_, Group>(
            r#"
            INSERT INTO groups (id, name, description, owner_id, created_at, updated_at)
            VALUES ($1, $2, $3, $4, NOW(), NOW())
            RETURNING id, name, description, owner_id, created_at, updated_at
            "#,
        )
        .bind(id)
        .bind(name)
        .bind(description)
        .bind(owner_id)
        .fetch_one(pool)
        .await
    }

    /// Get a group by ID
    pub async fn get_by_id(pool: &PgPool, id: Uuid) -> Result<Option<Group>, sqlx::Error> {
        sqlx::query_as::<_, Group>("SELECT id, name, description, owner_id, created_at, updated_at FROM groups WHERE id = $1")
            .bind(id)
            .fetch_optional(pool)
            .await
    }

    /// List all groups for a user
    pub async fn list_by_owner(pool: &PgPool, owner_id: Uuid) -> Result<Vec<Group>, sqlx::Error> {
        sqlx::query_as::<_, Group>(
            "SELECT id, name, description, owner_id, created_at, updated_at FROM groups WHERE owner_id = $1 ORDER BY created_at DESC",
        )
        .bind(owner_id)
        .fetch_all(pool)
        .await
    }

    /// Update a group
    pub async fn update(
        pool: &PgPool,
        id: Uuid,
        name: Option<String>,
        description: Option<String>,
    ) -> Result<Option<Group>, sqlx::Error> {
        // Build dynamic UPDATE query
        let mut query = String::from("UPDATE groups SET updated_at = NOW()");
        let mut param_count = 1;

        if name.is_some() {
            query.push_str(&format!(", name = ${}", param_count));
            param_count += 1;
        }

        if description.is_some() {
            query.push_str(&format!(", description = ${}", param_count));
            param_count += 1;
        }

        query.push_str(&format!(" WHERE id = ${} RETURNING id, name, description, owner_id, created_at, updated_at", param_count));

        let mut q = sqlx::query_as::<_, Group>(&query);

        if let Some(n) = name {
            q = q.bind(n);
        }
        if let Some(d) = description {
            q = q.bind(d);
        }
        q = q.bind(id);

        q.fetch_optional(pool).await
    }

    /// Delete a group
    pub async fn delete(pool: &PgPool, id: Uuid) -> Result<bool, sqlx::Error> {
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
        id: Uuid,
        group_id: Uuid,
        user_id: Uuid,
    ) -> Result<GroupMember, sqlx::Error> {
        sqlx::query_as::<_, GroupMember>(
            r#"
            INSERT INTO group_members (id, group_id, user_id, created_at)
            VALUES ($1, $2, $3, NOW())
            RETURNING id, group_id, user_id, created_at
            "#,
        )
        .bind(id)
        .bind(group_id)
        .bind(user_id)
        .fetch_one(pool)
        .await
    }

    /// Remove a member from a group
    pub async fn remove_member(pool: &PgPool, group_id: Uuid, user_id: Uuid) -> Result<bool, sqlx::Error> {
        let result = sqlx::query("DELETE FROM group_members WHERE group_id = $1 AND user_id = $2")
            .bind(group_id)
            .bind(user_id)
            .execute(pool)
            .await?;

        Ok(result.rows_affected() > 0)
    }

    /// Get all members of a group
    pub async fn get_members(pool: &PgPool, group_id: Uuid) -> Result<Vec<GroupMember>, sqlx::Error> {
        sqlx::query_as::<_, GroupMember>(
            "SELECT id, group_id, user_id, created_at FROM group_members WHERE group_id = $1 ORDER BY created_at",
        )
        .bind(group_id)
        .fetch_all(pool)
        .await
    }

    /// Check if a user is a member of a group
    pub async fn is_member(pool: &PgPool, group_id: Uuid, user_id: Uuid) -> Result<bool, sqlx::Error> {
        let result = sqlx::query("SELECT 1 FROM group_members WHERE group_id = $1 AND user_id = $2")
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
        id: Uuid,
        requester_id: Uuid,
        recipient_id: Uuid,
    ) -> Result<Friendship, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(
            r#"
            INSERT INTO friendships (id, requester_id, recipient_id, status, created_at, updated_at)
            VALUES ($1, $2, $3, 'PENDING', NOW(), NOW())
            RETURNING id, requester_id, recipient_id, status, created_at, updated_at
            "#,
        )
        .bind(id)
        .bind(requester_id)
        .bind(recipient_id)
        .fetch_one(pool)
        .await
    }

    /// Get a friendship by ID
    pub async fn get_by_id(pool: &PgPool, id: Uuid) -> Result<Option<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(
            "SELECT id, requester_id, recipient_id, status, created_at, updated_at FROM friendships WHERE id = $1",
        )
        .bind(id)
        .fetch_optional(pool)
        .await
    }

    /// Accept a friendship request
    pub async fn accept(pool: &PgPool, id: Uuid) -> Result<Option<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(
            "UPDATE friendships SET status = 'ACCEPTED', updated_at = NOW() WHERE id = $1 RETURNING id, requester_id, recipient_id, status, created_at, updated_at",
        )
        .bind(id)
        .fetch_optional(pool)
        .await
    }

    /// Decline a friendship request
    pub async fn decline(pool: &PgPool, id: Uuid) -> Result<Option<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(
            "UPDATE friendships SET status = 'DECLINED', updated_at = NOW() WHERE id = $1 RETURNING id, requester_id, recipient_id, status, created_at, updated_at",
        )
        .bind(id)
        .fetch_optional(pool)
        .await
    }

    /// Remove a friendship
    pub async fn delete(pool: &PgPool, id: Uuid) -> Result<bool, sqlx::Error> {
        let result = sqlx::query("DELETE FROM friendships WHERE id = $1")
            .bind(id)
            .execute(pool)
            .await?;

        Ok(result.rows_affected() > 0)
    }

    /// Get friendship between two users
    pub async fn get_friendship(
        pool: &PgPool,
        user1_id: Uuid,
        user2_id: Uuid,
    ) -> Result<Option<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(
            r#"
            SELECT id, requester_id, recipient_id, status, created_at, updated_at
            FROM friendships
            WHERE (requester_id = $1 AND recipient_id = $2)
               OR (requester_id = $2 AND recipient_id = $1)
            "#,
        )
        .bind(user1_id)
        .bind(user2_id)
        .fetch_optional(pool)
        .await
    }

    /// List all friendships for a user (pending and accepted)
    pub async fn list_for_user(pool: &PgPool, user_id: Uuid) -> Result<Vec<Friendship>, sqlx::Error> {
        sqlx::query_as::<_, Friendship>(
            r#"
            SELECT id, requester_id, recipient_id, status, created_at, updated_at
            FROM friendships
            WHERE requester_id = $1 OR recipient_id = $1
            ORDER BY created_at DESC
            "#,
        )
        .bind(user_id)
        .fetch_all(pool)
        .await
    }
}
