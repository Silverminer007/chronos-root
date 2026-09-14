-- Performance optimization: Add indexes for common queries
-- This migration addresses N+1 query patterns and common filter operations

-- Composite index for listing appointments by creator with time filtering
CREATE INDEX idx_appointments_creator_start_time ON appointments(creator_id, start_time DESC)
WHERE deleted_at IS NULL;

-- Index for finding appointments in a time range (common query pattern)
CREATE INDEX idx_appointments_time_range ON appointments(start_time, end_time)
WHERE deleted_at IS NULL;

-- Composite index for finding participants by appointment and status
CREATE INDEX idx_participants_appointment_status ON appointment_participants(appointment_id, status)
WHERE deleted_at IS NULL;

-- Index for finding user's appointments (participant view)
CREATE INDEX idx_participants_user_status ON appointment_participants(user_id, status, appointment_id)
WHERE deleted_at IS NULL;

-- Index for group member queries
CREATE INDEX idx_group_members_group_user ON group_members(group_id, user_id)
WHERE deleted_at IS NULL;

-- Index for friendship queries (bidirectional lookup)
CREATE INDEX idx_friendships_user_friend ON friendships(user_id, friend_id)
WHERE deleted_at IS NULL;

CREATE INDEX idx_friendships_friend_user ON friendships(friend_id, user_id)
WHERE deleted_at IS NULL;

-- Index for created_at filtering (recent appointments)
CREATE INDEX idx_appointments_created_at ON appointments(created_at DESC)
WHERE deleted_at IS NULL;

-- Index for updated_at filtering (recently modified)
CREATE INDEX idx_appointments_updated_at ON appointments(updated_at DESC)
WHERE deleted_at IS NULL;

-- Partial index: only active appointments (common filter)
CREATE INDEX idx_active_appointments ON appointments(start_time)
WHERE deleted_at IS NULL AND start_time > NOW();

-- Add ANALYZE to update table statistics
ANALYZE users;
ANALYZE groups;
ANALYZE appointments;
ANALYZE appointment_participants;
ANALYZE group_members;
ANALYZE friendships;
