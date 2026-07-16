-- POC Migration V2.0.0: Remove users table, use Keycloak oidcid as user identity
--
-- Strategy:
--   1. Add VARCHAR oidcid columns to every table that currently FK'd to users.id
--   2. Backfill from users.oidcid via JOIN
--   3. Drop FK constraints and old BIGINT columns
--   4. Drop the users table
--
-- After this migration, every "user reference" column holds the Keycloak subject UUID (VARCHAR).

-- ─────────────────────────────────────────────────────────────────────────────
-- Pre-flight validation
-- Every user must have a Keycloak OIDC subject assigned before we can backfill.
-- If any row is missing an oidcid the JOIN-based UPDATE will silently produce
-- NULL values, which the subsequent SET NOT NULL will then reject with a
-- cryptic error. Fail fast here with a clear message instead.
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
    bad_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO bad_count FROM users WHERE oidcid IS NULL OR oidcid = '';
    IF bad_count > 0 THEN
        RAISE EXCEPTION
            'V2.0.0 pre-flight failed: % user row(s) have a NULL or empty oidcid. '
            'All users must have logged in via Keycloak before this migration can run.',
            bad_count;
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- appointment_participation  (user_id BIGINT → user_oidcid VARCHAR)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE appointment_participation ADD COLUMN user_oidcid VARCHAR(255);

UPDATE appointment_participation ap
SET user_oidcid = u.oidcid
FROM users u
WHERE ap.user_id = u.id;

ALTER TABLE appointment_participation ALTER COLUMN user_oidcid SET NOT NULL;
ALTER TABLE appointment_participation DROP CONSTRAINT IF EXISTS fk_appointment_participation_user;
ALTER TABLE appointment_participation DROP COLUMN user_id;
ALTER TABLE appointment_participation DROP CONSTRAINT IF EXISTS uk_appointment_participation_user;
ALTER TABLE appointment_participation ADD CONSTRAINT uk_appointment_participation_user UNIQUE (appointment_id, user_oidcid);

-- ─────────────────────────────────────────────────────────────────────────────
-- friendship_requests  (requester_id / addressee_id BIGINT → VARCHAR)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE friendship_requests ADD COLUMN requester_oidcid VARCHAR(255);
ALTER TABLE friendship_requests ADD COLUMN addressee_oidcid VARCHAR(255);

UPDATE friendship_requests fr
SET requester_oidcid = u.oidcid
FROM users u
WHERE fr.requester_id = u.id;

UPDATE friendship_requests fr
SET addressee_oidcid = u.oidcid
FROM users u
WHERE fr.addressee_id = u.id;

ALTER TABLE friendship_requests ALTER COLUMN requester_oidcid SET NOT NULL;
ALTER TABLE friendship_requests ALTER COLUMN addressee_oidcid SET NOT NULL;
ALTER TABLE friendship_requests DROP COLUMN requester_id;
ALTER TABLE friendship_requests DROP COLUMN addressee_id;

-- Update indexes for the new column names
DROP INDEX IF EXISTS idx_friendship_requester;
DROP INDEX IF EXISTS idx_friendship_addressee;
CREATE INDEX idx_friendship_requester ON friendship_requests (requester_oidcid);
CREATE INDEX idx_friendship_addressee ON friendship_requests (addressee_oidcid);

DROP INDEX IF EXISTS idx_friendship_status_requester_addressee;
CREATE INDEX idx_friendship_status_requester_addressee
    ON friendship_requests (status, requester_oidcid, addressee_oidcid);

-- ─────────────────────────────────────────────────────────────────────────────
-- group_member  (user_id BIGINT → user_oidcid VARCHAR)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE group_member ADD COLUMN user_oidcid VARCHAR(255);

UPDATE group_member gm
SET user_oidcid = u.oidcid
FROM users u
WHERE gm.user_id = u.id;

ALTER TABLE group_member ALTER COLUMN user_oidcid SET NOT NULL;
ALTER TABLE group_member DROP CONSTRAINT IF EXISTS fk_group_member_user;
ALTER TABLE group_member DROP COLUMN user_id;

DROP INDEX IF EXISTS idx_group_member_user;
CREATE INDEX idx_group_member_user ON group_member (user_oidcid);

-- ─────────────────────────────────────────────────────────────────────────────
-- groups  (owner_id BIGINT → owner_oidcid VARCHAR)
-- Groups with no valid owner are unmanageable and are dropped before migration.
-- Deletion order respects FK constraints:
--   appointment_participation (no FK, but references agp rows) →
--   appointment_group_participations (FK ON DELETE NO ACTION → groups) →
--   groups (group_member cascades via ON DELETE CASCADE from V1.4.0)
-- ─────────────────────────────────────────────────────────────────────────────
DELETE FROM appointment_participation
WHERE group_participation_id IN (
    SELECT id FROM appointment_group_participations
    WHERE group_id IN (
        SELECT id FROM groups
        WHERE owner_id IS NULL
           OR NOT EXISTS (SELECT 1 FROM users u WHERE u.id = groups.owner_id)
    )
);

DELETE FROM appointment_group_participations
WHERE group_id IN (
    SELECT id FROM groups
    WHERE owner_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM users u WHERE u.id = groups.owner_id)
);

DELETE FROM groups
WHERE owner_id IS NULL
   OR NOT EXISTS (SELECT 1 FROM users u WHERE u.id = groups.owner_id);

ALTER TABLE groups ADD COLUMN owner_oidcid VARCHAR(255);

UPDATE groups g
SET owner_oidcid = u.oidcid
FROM users u
WHERE g.owner_id = u.id;

ALTER TABLE groups ALTER COLUMN owner_oidcid SET NOT NULL;
ALTER TABLE groups DROP CONSTRAINT IF EXISTS fk_groups_owner;
ALTER TABLE groups DROP COLUMN owner_id;

DROP INDEX IF EXISTS idx_groups_owner;
CREATE INDEX idx_groups_owner ON groups (owner_oidcid);

-- ─────────────────────────────────────────────────────────────────────────────
-- message  (sender_id BIGINT → sender_oidcid VARCHAR)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE message ADD COLUMN sender_oidcid VARCHAR(255);

UPDATE message m
SET sender_oidcid = u.oidcid
FROM users u
WHERE m.sender_id = u.id;

ALTER TABLE message DROP CONSTRAINT IF EXISTS fk_message_sender;
ALTER TABLE message DROP COLUMN sender_id;

DROP INDEX IF EXISTS idx_message_sender;
CREATE INDEX idx_message_sender ON message (sender_oidcid);

-- ─────────────────────────────────────────────────────────────────────────────
-- pushsubscription  (user_id BIGINT → user_oidcid VARCHAR)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE pushsubscription ADD COLUMN user_oidcid VARCHAR(255);

UPDATE pushsubscription ps
SET user_oidcid = u.oidcid
FROM users u
WHERE ps.user_id = u.id;

ALTER TABLE pushsubscription ALTER COLUMN user_oidcid SET NOT NULL;
ALTER TABLE pushsubscription DROP CONSTRAINT IF EXISTS fk_pushsubscription_user;
ALTER TABLE pushsubscription DROP COLUMN user_id;

DROP INDEX IF EXISTS idx_pushsubscription_user;
CREATE INDEX idx_pushsubscription_user ON pushsubscription (user_oidcid);

-- ─────────────────────────────────────────────────────────────────────────────
-- settings  (user_id BIGINT → user_oidcid VARCHAR)
-- ─────────────────────────────────────────────────────────────────────────────
-- Delete orphaned settings rows (user_id was nullable in V1)
DELETE FROM settings WHERE user_id IS NULL;

ALTER TABLE settings ADD COLUMN user_oidcid VARCHAR(255);

UPDATE settings s
SET user_oidcid = u.oidcid
FROM users u
WHERE s.user_id = u.id;

ALTER TABLE settings ALTER COLUMN user_oidcid SET NOT NULL;
DROP INDEX IF EXISTS uk3g6qnpteuj5qclay1c6a83ey8;
ALTER TABLE settings DROP CONSTRAINT IF EXISTS fk_settings_user;
ALTER TABLE settings DROP COLUMN user_id;
CREATE UNIQUE INDEX idx_settings_user_oidcid ON settings (user_oidcid);

-- ─────────────────────────────────────────────────────────────────────────────
-- push_notification_log  (user_id BIGINT → user_oidcid VARCHAR)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE push_notification_log ADD COLUMN user_oidcid VARCHAR(255);

UPDATE push_notification_log pnl
SET user_oidcid = u.oidcid
FROM users u
WHERE pnl.user_id = u.id;

ALTER TABLE push_notification_log ALTER COLUMN user_oidcid SET NOT NULL;
DROP INDEX IF EXISTS idx_push_notification_log_user_id;
ALTER TABLE push_notification_log DROP COLUMN user_id;
CREATE INDEX idx_push_notification_log_user_oidcid ON push_notification_log (user_oidcid);

-- users table is dropped in V2.1.0 after user_profiles cache has been seeded