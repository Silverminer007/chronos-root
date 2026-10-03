-- Performance optimization: Add indexes for common queries
-- This migration addresses N+1 query patterns and common filter operations

-- Composite index for listing appointments by creator with time filtering
CREATE INDEX idx_appointments_creator_start_time ON appointments(creator_id, start_time DESC);

-- Index for finding appointments in a time range (common query pattern)
CREATE INDEX idx_appointments_time_range ON appointments(start_time, end_time);

-- Composite index for finding participants by appointment and status
CREATE INDEX idx_participants_appointment_status ON appointment_participants(appointment_id, status);

-- Index for finding user's appointments (participant view)
CREATE INDEX idx_participants_user_status ON appointment_participants(user_id, status, appointment_id);

-- Index for group member queries
CREATE INDEX idx_group_members_group_user ON group_members(group_id, user_id);

-- Index for friendship queries (bidirectional lookup using actual column names)
CREATE INDEX idx_friendships_requester_recipient ON friendships(requester_id, recipient_id);

CREATE INDEX idx_friendships_recipient_requester ON friendships(recipient_id, requester_id);

-- Index for created_at filtering (recent appointments)
CREATE INDEX idx_appointments_created_at ON appointments(created_at DESC);

-- Index for updated_at filtering (recently modified)
CREATE INDEX idx_appointments_updated_at ON appointments(updated_at DESC);

-- Add ANALYZE to update table statistics
ANALYZE users;
ANALYZE groups;
ANALYZE appointments;
ANALYZE appointment_participants;
ANALYZE group_members;
ANALYZE friendships;
