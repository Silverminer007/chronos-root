-- Flyway Migration V1.5.0: Add user timestamp columns and indexes for friendship search performance
-- Optimizes the findNonFriends and findRecentNonFriends queries

-- Add timestamp columns to users table
ALTER TABLE users ADD COLUMN created_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE users ADD COLUMN last_update TIMESTAMP(6) WITH TIME ZONE;

-- Initialize existing rows with current timestamp
UPDATE users SET created_at = NOW(), last_update = NOW() WHERE created_at IS NULL;

-- Composite index for the NOT EXISTS subquery in findNonFriends
-- Covers lookups by status + requester/addressee combinations
CREATE INDEX idx_friendship_status_requester_addressee
    ON friendship_requests (status, requester_id, addressee_id);

-- Indexes on users table for LIKE searches on name fields
-- Note: email already has a UNIQUE constraint which provides an index
CREATE INDEX idx_users_firstname_lower ON users (LOWER(firstname));
CREATE INDEX idx_users_lastname_lower ON users (LOWER(lastname));
CREATE INDEX idx_users_email_lower ON users (LOWER(email));

-- Index for ORDER BY createdAt DESC in findRecentNonFriends
CREATE INDEX idx_users_created_at ON users (created_at DESC NULLS LAST);
