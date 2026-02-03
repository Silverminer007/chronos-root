CREATE TABLE push_notification_log
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT                      NOT NULL,
    notification_type VARCHAR(100),
    payload          TEXT                         NOT NULL,
    endpoint         VARCHAR(2048)                NOT NULL,
    http_status_code INTEGER,
    success          BOOLEAN                      NOT NULL,
    error_message    VARCHAR(1000),
    created_at       TIMESTAMP(6) WITH TIME ZONE  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_push_notification_log_user_id ON push_notification_log (user_id);
CREATE INDEX idx_push_notification_log_created_at ON push_notification_log (created_at DESC);
CREATE INDEX idx_push_notification_log_success ON push_notification_log (success) WHERE success = FALSE;
