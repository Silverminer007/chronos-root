ALTER TABLE settings
DROP COLUMN IF EXISTS appointment_cancelled,
    DROP COLUMN IF EXISTS appointment_message,
    DROP COLUMN IF EXISTS appointment_moved,
    DROP COLUMN IF EXISTS appointment_participant_added,
    DROP COLUMN IF EXISTS appointment_participation_invalid,
    DROP COLUMN IF EXISTS appointment_participation_status_pending,
    DROP COLUMN IF EXISTS appointment_reminder,
    DROP COLUMN IF EXISTS group_member_added,
    DROP COLUMN IF EXISTS appointment_participation_status_changed;

-- Add new notification columns with defaults
ALTER TABLE settings
    ADD COLUMN appointment_cancelled                    VARCHAR(255) NOT NULL DEFAULT 'ALL',
    ADD COLUMN appointment_message                      VARCHAR(255) NOT NULL DEFAULT 'ATTENDANT',
    ADD COLUMN appointment_moved                        VARCHAR(255) NOT NULL DEFAULT 'ALL',
    ADD COLUMN appointment_participant_added            VARCHAR(255) NOT NULL DEFAULT 'RESPONSIBLE',
    ADD COLUMN appointment_participation_invalid        VARCHAR(255) NOT NULL DEFAULT 'ATTENDANT',
    ADD COLUMN appointment_participation_status_changed VARCHAR(255) NOT NULL DEFAULT 'RESPONSIBLE',
    ADD COLUMN appointment_participation_status_pending VARCHAR(255) NOT NULL DEFAULT 'ATTENDANT',
    ADD COLUMN appointment_reminder                     VARCHAR(255) NOT NULL DEFAULT 'ALL',
    ADD COLUMN group_member_added                       VARCHAR(255) NOT NULL DEFAULT 'DISABLED';