-- Baseline: the final schema of the Java backend (Flyway V1.0.0 ... V2.1.0), see
-- kb/adr/0013-rust-schema-identical-to-java.md.
--
-- Ids are BIGINT. Tables without a column default take their ids from the matching
-- <table>_seq sequence (INCREMENT BY 50, as Hibernate does); the tables that Java created
-- with BIGSERIAL keep their <table>_id_seq default. Users are identified by their Keycloak
-- subject (*_oidcid, VARCHAR) - there is no users table. Enums are stored as the same strings
-- as in Java; appointment.status is the ordinal of Java's AppointmentStatus (SMALLINT).
--
-- schema/snapshot.sql + tests/schema_parity_test.rs prove this file equals the Java schema.

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

CREATE TABLE groups
(
    id           BIGSERIAL PRIMARY KEY,
    groupname    VARCHAR(255),
    owner_oidcid VARCHAR(255) NOT NULL
);

CREATE TABLE appointment_group_participations
(
    id             BIGINT PRIMARY KEY NOT NULL,
    role           VARCHAR(255) NOT NULL,
    appointment_id BIGINT       NOT NULL,
    group_id       BIGINT       NOT NULL,
    CONSTRAINT fk_appointment_group_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointment (id),
    CONSTRAINT fk_appointment_group_group FOREIGN KEY (group_id)
        REFERENCES groups (id)
);

CREATE TABLE appointment_participation
(
    id                     BIGINT PRIMARY KEY NOT NULL,
    group_participation_id BIGINT,
    role                   VARCHAR(255) NOT NULL,
    status                 VARCHAR(255) NOT NULL,
    appointment_id         BIGINT       NOT NULL,
    user_oidcid            VARCHAR(255) NOT NULL,
    CONSTRAINT fk_appointment_participation_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointment (id),
    CONSTRAINT uk_appointment_participation_user UNIQUE (appointment_id, user_oidcid)
);

CREATE TABLE friendship_requests
(
    id               BIGINT PRIMARY KEY          NOT NULL,
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    responded_at     TIMESTAMP(6) WITH TIME ZONE,
    status           VARCHAR(255)                NOT NULL,
    requester_oidcid VARCHAR(255)                NOT NULL,
    addressee_oidcid VARCHAR(255)                NOT NULL
);

CREATE TABLE group_member
(
    id          BIGINT PRIMARY KEY NOT NULL,
    group_id    BIGINT             NOT NULL,
    user_oidcid VARCHAR(255)       NOT NULL,
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id)
        REFERENCES groups (id) ON DELETE CASCADE
);

CREATE TABLE message
(
    id             BIGSERIAL PRIMARY KEY,
    "timestamp"    TIMESTAMP(6) WITH TIME ZONE,
    body           VARCHAR(255),
    appointment_id BIGINT,
    sender_oidcid  VARCHAR(255) NOT NULL,
    CONSTRAINT fk_message_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointment (id)
);

CREATE TABLE push_notification_log
(
    id                BIGSERIAL PRIMARY KEY,
    notification_type VARCHAR(100),
    payload           TEXT                        NOT NULL,
    endpoint          VARCHAR(2048)               NOT NULL,
    http_status_code  INTEGER,
    success           BOOLEAN                     NOT NULL,
    error_message     VARCHAR(1000),
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT NOW(),
    user_oidcid       VARCHAR(255)                NOT NULL
);

CREATE TABLE pushsubscription
(
    id          BIGSERIAL PRIMARY KEY,
    auth        VARCHAR(255) NOT NULL,
    endpoint    VARCHAR(255) NOT NULL,
    p256dh      VARCHAR(255) NOT NULL,
    user_oidcid VARCHAR(255) NOT NULL
);

CREATE TABLE settings
(
    id                                       BIGSERIAL PRIMARY KEY,
    appointment_cancelled                    VARCHAR(255) NOT NULL DEFAULT 'ALL',
    appointment_message                      VARCHAR(255) NOT NULL DEFAULT 'ATTENDANT',
    appointment_moved                        VARCHAR(255) NOT NULL DEFAULT 'ALL',
    appointment_participant_added            VARCHAR(255) NOT NULL DEFAULT 'RESPONSIBLE',
    appointment_participation_invalid        VARCHAR(255) NOT NULL DEFAULT 'ATTENDANT',
    appointment_participation_status_changed VARCHAR(255) NOT NULL DEFAULT 'RESPONSIBLE',
    appointment_participation_status_pending VARCHAR(255) NOT NULL DEFAULT 'ATTENDANT',
    appointment_reminder                     VARCHAR(255) NOT NULL DEFAULT 'ALL',
    group_member_added                       VARCHAR(255) NOT NULL DEFAULT 'DISABLED',
    user_oidcid                              VARCHAR(255) NOT NULL
);

CREATE TABLE user_profiles
(
    oidc_id             VARCHAR(255) NOT NULL PRIMARY KEY,
    first_name          VARCHAR(255),
    last_name           VARCHAR(255),
    email               VARCHAR(255),
    profile_picture_url VARCHAR(512),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Hibernate sequences (users_seq is a leftover of the removed users table, kept for parity)
CREATE SEQUENCE appointment_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE appointment_group_participations_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE appointment_participation_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE friendship_requests_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE group_member_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE users_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE groups_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE message_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE pushsubscription_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE settings_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE push_notification_log_seq START WITH 1 INCREMENT BY 50;

CREATE INDEX idx_appointment_start_time ON appointment (start_time);
CREATE INDEX idx_appointment_end_time ON appointment (end_time);
CREATE INDEX idx_appointment_status ON appointment (status);
CREATE INDEX idx_appointment_group_participations_appointment ON appointment_group_participations (appointment_id);
CREATE INDEX idx_appointment_group_participations_group ON appointment_group_participations (group_id);
CREATE INDEX idx_appointment_participation_appointment ON appointment_participation (appointment_id);
CREATE INDEX idx_friendship_requester ON friendship_requests (requester_oidcid);
CREATE INDEX idx_friendship_addressee ON friendship_requests (addressee_oidcid);
CREATE INDEX idx_friendship_status ON friendship_requests (status);
CREATE INDEX idx_friendship_status_requester_addressee
    ON friendship_requests (status, requester_oidcid, addressee_oidcid);
CREATE INDEX idx_group_member_group ON group_member (group_id);
CREATE INDEX idx_group_member_user ON group_member (user_oidcid);
CREATE INDEX idx_groups_owner ON groups (owner_oidcid);
CREATE INDEX idx_message_appointment ON message (appointment_id);
CREATE INDEX idx_message_sender ON message (sender_oidcid);
CREATE INDEX idx_push_notification_log_created_at ON push_notification_log (created_at DESC);
CREATE INDEX idx_push_notification_log_success ON push_notification_log (success) WHERE success = FALSE;
CREATE INDEX idx_push_notification_log_user_oidcid ON push_notification_log (user_oidcid);
CREATE INDEX idx_pushsubscription_user ON pushsubscription (user_oidcid);
CREATE UNIQUE INDEX idx_settings_user_oidcid ON settings (user_oidcid);
CREATE INDEX idx_user_profiles_name ON user_profiles (lower(first_name || ' ' || last_name));
CREATE INDEX idx_user_profiles_email ON user_profiles (lower(email));
