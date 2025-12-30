-- Flyway Migration V1.1.0: Add appointments system and refactor notifications
-- This migration includes safe data migration from old to new structure

-- =====================================================
-- STEP 1: Create new tables
-- =====================================================

-- Add profilepictureurl to users table
ALTER TABLE users
    ADD COLUMN profilepictureurl VARCHAR(255);

-- Create appointment table
CREATE TABLE appointment
(
    id               BIGINT PRIMARY KEY NOT NULL,
    createdat        TIMESTAMP(6) WITH TIME ZONE,
    description      VARCHAR(255),
    end_time         TIMESTAMP(6) WITH TIME ZONE,
    lastupdate       TIMESTAMP(6) WITH TIME ZONE,
    minimalattendees INTEGER,
    name             VARCHAR(255),
    start_time       TIMESTAMP(6) WITH TIME ZONE,
    status           SMALLINT,
    venue            VARCHAR(255)
);

-- Create appointment group participations table
CREATE TABLE appointment_group_participations
(
    id             BIGINT PRIMARY KEY NOT NULL,
    role           VARCHAR(255) NOT NULL,
    appointment_id BIGINT       NOT NULL,
    group_id       BIGINT       NOT NULL,
    CONSTRAINT fk_appointment_group_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointment (id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_appointment_group_group FOREIGN KEY (group_id)
        REFERENCES groups (id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

-- Create appointment participation table
CREATE TABLE appointment_participation
(
    id                     BIGINT PRIMARY KEY NOT NULL,
    group_participation_id BIGINT,
    role                   VARCHAR(255) NOT NULL,
    status                 VARCHAR(255) NOT NULL,
    appointment_id         BIGINT       NOT NULL,
    user_id                BIGINT       NOT NULL,
    CONSTRAINT fk_appointment_participation_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_appointment_participation_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointment (id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT uk_appointment_participation_user UNIQUE (appointment_id, user_id)
);

-- Create friendship requests table
CREATE TABLE friendship_requests
(
    id           BIGINT PRIMARY KEY NOT NULL,
    addressee_id BIGINT                      NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    requester_id BIGINT                      NOT NULL,
    responded_at TIMESTAMP(6) WITH TIME ZONE,
    status       VARCHAR(255)                NOT NULL
);

-- Create group member table
CREATE TABLE group_member
(
    id       BIGINT PRIMARY KEY NOT NULL,
    group_id BIGINT NOT NULL,
    user_id  BIGINT NOT NULL,
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id)
        REFERENCES groups (id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_group_member_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

-- Create sequences for Hibernate (after tables, so we can calculate start values)
-- Note: No quotes around sequence names to allow PostgreSQL to lowercase them,
-- matching Hibernate's default naming strategy
CREATE SEQUENCE appointment_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE appointment_group_participations_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE appointment_participation_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE friendship_requests_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE group_member_SEQ START WITH 1 INCREMENT BY 50;

-- =====================================================
-- STEP 2: Migrate existing data
-- =====================================================

-- Migrate events to appointments
INSERT INTO appointment (id, createdat, description, end_time, lastupdate, minimalattendees, name, start_time, status,
                         venue)
SELECT id,
       createdat,
       description,
       endtime,
       lastupdate,
       minimalattendees,
       name,
       starttime,
       eventstatus,
       venue
FROM event;

-- Update the sequence to continue from the highest ID
SELECT setval('appointment_seq', COALESCE((SELECT MAX(id) FROM appointment), 0) + 1, false);

-- Migrate event group attendees to appointment group participations
INSERT INTO appointment_group_participations (id, role, appointment_id, group_id)
SELECT id,
       CASE
           WHEN role = 0 THEN 'RESPONSIBLE'
           WHEN role = 1 THEN 'ATTENDANT'
           ELSE 'ATTENDANT'
           END as role,
       event_id,
       group_id
FROM eventgroupattendees;

SELECT setval('appointment_group_participations_seq', COALESCE((SELECT MAX(id) FROM appointment_group_participations), 0) + 1, false);

-- Migrate event user attendees to appointment participation (individual invites)
-- Do this FIRST to prioritize individual invitations
INSERT INTO appointment_participation (id, role, status, appointment_id, user_id)
SELECT id,
       CASE
           WHEN role = 0 THEN 'RESPONSIBLE'
           WHEN role = 1 THEN 'ATTENDANT'
           ELSE 'ATTENDANT'
           END   as role,
       'PENDING' as status,
       event_id,
       user_id
FROM eventuserattendees;

-- For each eventgroupattendees entry, create appointment_participation entries for all group members
-- group_participation_id links back to the appointment_group_participations.id
-- Use ON CONFLICT to skip users who were already invited individually
INSERT INTO appointment_participation (id, group_participation_id, role, status, appointment_id, user_id)
SELECT (SELECT COALESCE(MAX(id), 0) FROM appointment_participation) + ROW_NUMBER() OVER () as id,
       ega.id        as group_participation_id,
       CASE
           WHEN ega.role = 0 THEN 'RESPONSIBLE'
           WHEN ega.role = 1 THEN 'ATTENDANT'
           ELSE 'ATTENDANT'
           END       as role,
       'PENDING'     as status,
       ega.event_id  as appointment_id,
       gu.members_id as user_id
FROM eventgroupattendees ega
         INNER JOIN groups_users gu ON ega.group_id = gu.group_id
ON CONFLICT (appointment_id, user_id) DO NOTHING;

-- Update appointment_participation with status information from attendance table
-- attendance and eventuserattendees contain the same users but attendance has the status
UPDATE appointment_participation ap
SET status = CASE
                 WHEN a.status = 0 THEN 'PENDING'
                 WHEN a.status = 1 THEN 'APPROVED'
                 WHEN a.status = 2 THEN 'REJECTED'
                 ELSE 'PENDING'
    END
FROM attendance a
WHERE ap.user_id = a.user_id
  AND ap.appointment_id = a.event_id
  AND ap.group_participation_id IS NULL; -- Only update individual participations

SELECT setval('appointment_participation_seq', COALESCE((SELECT MAX(id) FROM appointment_participation), 0) + 1, false);

-- Migrate groups_users to group_member
INSERT INTO group_member (id, group_id, user_id)
SELECT nextval('group_member_seq'), group_id, members_id
FROM groups_users;

-- Migrate contacts to friendship_requests
-- Assuming mutual contacts should become accepted friendship requests
INSERT INTO friendship_requests (id, addressee_id, created_at, requester_id, responded_at, status)
SELECT nextval('friendship_requests_seq'),
       c.contact_id as addressee_id,
       NOW()        as created_at, -- No timestamp available in original
       c.user_id    as requester_id,
       NOW()        as responded_at,
       'ACCEPTED'   as status
FROM contact c
WHERE EXISTS (SELECT 1
              FROM contact c2
              WHERE c2.user_id = c.contact_id
                AND c2.contact_id = c.user_id)
-- Only insert once per friendship pair
  AND c.user_id < c.contact_id;

-- =====================================================
-- STEP 3: Backup old message data in temporary table
-- =====================================================

-- Create temporary backup table for messages
CREATE TABLE message_backup AS
SELECT *
FROM message;

-- =====================================================
-- STEP 4: Modify message table
-- =====================================================

-- Remove foreign key constraint first
ALTER TABLE message
    DROP CONSTRAINT IF EXISTS fk_message_event;

-- Drop old columns
ALTER TABLE message
    DROP COLUMN IF EXISTS message,
    DROP COLUMN IF EXISTS title,
    DROP COLUMN IF EXISTS event_id;

-- Add new columns
ALTER TABLE message
    ADD COLUMN body           VARCHAR(255),
    ADD COLUMN appointment_id BIGINT;

-- Change timestamp to WITH TIME ZONE
ALTER TABLE message
    ALTER COLUMN timestamp TYPE TIMESTAMP(6) WITH TIME ZONE;

-- Migrate message data (copy title or message to body)
UPDATE message
SET body = COALESCE(
        (SELECT COALESCE(mb.title, mb.message) FROM message_backup mb WHERE mb.id = message.id),
        ''
           );

-- Link messages to appointments (event_id -> appointment_id)
UPDATE message
SET appointment_id = (SELECT mb.event_id FROM message_backup mb WHERE mb.id = message.id);

-- Add foreign key for appointment_id
ALTER TABLE message
    ADD CONSTRAINT fk_message_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointment (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- =====================================================
-- STEP 5: Modify settings table
-- =====================================================

-- Create backup
CREATE TABLE settings_backup AS
SELECT *
FROM settings;

-- Drop old notification columns
ALTER TABLE settings
    DROP COLUMN IF EXISTS attendancestatuschangednotifications,
    DROP COLUMN IF EXISTS contactsnotifications,
    DROP COLUMN IF EXISTS eventchangednotifications,
    DROP COLUMN IF EXISTS eventremindersnotifications,
    DROP COLUMN IF EXISTS groupmembershipnotifications,
    DROP COLUMN IF EXISTS messagesnotifications;

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

-- Drop defaults after initial population
ALTER TABLE settings
    ALTER COLUMN appointment_cancelled DROP DEFAULT,
    ALTER COLUMN appointment_message DROP DEFAULT,
    ALTER COLUMN appointment_moved DROP DEFAULT,
    ALTER COLUMN appointment_participant_added DROP DEFAULT,
    ALTER COLUMN appointment_participation_invalid DROP DEFAULT,
    ALTER COLUMN appointment_participation_status_changed DROP DEFAULT,
    ALTER COLUMN appointment_participation_status_pending DROP DEFAULT,
    ALTER COLUMN appointment_reminder DROP DEFAULT,
    ALTER COLUMN group_member_added DROP DEFAULT;

-- =====================================================
-- STEP 7: Drop old tables (only after successful migration)
-- =====================================================

DROP TABLE IF EXISTS attendance CASCADE;
DROP TABLE IF EXISTS contact CASCADE;
DROP TABLE IF EXISTS eventgroupattendees CASCADE;
DROP TABLE IF EXISTS eventseries CASCADE;
DROP TABLE IF EXISTS eventuserattendees CASCADE;
DROP TABLE IF EXISTS groups_users CASCADE;
DROP TABLE IF EXISTS event CASCADE;

-- Drop backup tables (optional - keep them for a while in production)
-- DROP TABLE IF EXISTS message_backup;
-- DROP TABLE IF EXISTS settings_backup;

-- =====================================================
-- STEP 8: Create indexes for performance
-- =====================================================

-- Indexes for appointment tables
CREATE INDEX idx_appointment_start_time ON appointment (start_time);
CREATE INDEX idx_appointment_end_time ON appointment (end_time);
CREATE INDEX idx_appointment_status ON appointment (status);
CREATE INDEX idx_appointment_group_participations_appointment ON appointment_group_participations (appointment_id);
CREATE INDEX idx_appointment_group_participations_group ON appointment_group_participations (group_id);
CREATE INDEX idx_appointment_participation_appointment ON appointment_participation (appointment_id);
CREATE INDEX idx_appointment_participation_user ON appointment_participation (user_id);

-- Indexes for friendship requests
CREATE INDEX idx_friendship_requester ON friendship_requests (requester_id);
CREATE INDEX idx_friendship_addressee ON friendship_requests (addressee_id);
CREATE INDEX idx_friendship_status ON friendship_requests (status);

-- Indexes for group member table
CREATE INDEX idx_group_member_group ON group_member (group_id);
CREATE INDEX idx_group_member_user ON group_member (user_id);

-- Index for message appointment_id
CREATE INDEX idx_message_appointment ON message (appointment_id);