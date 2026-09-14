-- Create push_subscriptions table for storing user web push subscriptions
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    endpoint VARCHAR(1000) NOT NULL,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_subscription UNIQUE(user_id, endpoint)
);

-- Index for queries by user_id
CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions(user_id);

-- Index for queries by endpoint (to check if subscription exists)
CREATE INDEX idx_push_subscriptions_endpoint ON push_subscriptions(endpoint);
