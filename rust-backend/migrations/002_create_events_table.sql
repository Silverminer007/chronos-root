-- Create events table for event bus
-- Stores all fired events for persistence and replay
CREATE TABLE IF NOT EXISTS events (
    id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    observed_at TIMESTAMPTZ
);

-- Index for queries by event type
CREATE INDEX idx_events_event_type ON events(event_type);

-- Index for queries by timestamp (for replaying events)
CREATE INDEX idx_events_timestamp ON events(timestamp);

-- Index for unobserved events (where observed_at IS NULL)
CREATE INDEX idx_events_unobserved ON events(observed_at) WHERE observed_at IS NULL;
