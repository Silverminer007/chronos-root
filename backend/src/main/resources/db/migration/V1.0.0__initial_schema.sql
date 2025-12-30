-- Flyway Migration V1.0.0: Initial database schema

-- Users table (must be created first due to foreign key dependencies)
CREATE TABLE users
(
    id        BIGSERIAL PRIMARY KEY,
    email     VARCHAR(255) UNIQUE,
    firstname VARCHAR(255),
    lastname  VARCHAR(255),
    oidcid    VARCHAR(255) UNIQUE
);

-- Groups table
CREATE TABLE groups
(
    id        BIGSERIAL PRIMARY KEY,
    groupname VARCHAR(255),
    owner_id  BIGINT,
    CONSTRAINT fk_groups_owner FOREIGN KEY (owner_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

-- Events table
CREATE TABLE event
(
    id               BIGSERIAL PRIMARY KEY,
    createdat        TIMESTAMP(6) WITH TIME ZONE,
    description      VARCHAR(255),
    endtime          TIMESTAMP(6) WITH TIME ZONE,
    eventstatus      SMALLINT,
    lastupdate       TIMESTAMP(6) WITH TIME ZONE,
    minimalattendees INTEGER,
    name             VARCHAR(255),
    starttime        TIMESTAMP(6) WITH TIME ZONE,
    venue            VARCHAR(255)
);

-- Attendance table
CREATE TABLE attendance
(
    id       BIGSERIAL PRIMARY KEY,
    status   SMALLINT,
    event_id BIGINT,
    user_id  BIGINT,
    CONSTRAINT fk_attendance_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_attendance_event FOREIGN KEY (event_id)
        REFERENCES event (id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

-- Contact table
CREATE TABLE contact
(
    id         BIGSERIAL PRIMARY KEY,
    contact_id BIGINT,
    user_id    BIGINT,
    CONSTRAINT fk_contact_contact FOREIGN KEY (contact_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_contact_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

-- Event group attendees table
CREATE TABLE eventgroupattendees
(
    id       BIGSERIAL PRIMARY KEY,
    role     SMALLINT,
    event_id BIGINT,
    group_id BIGINT,
    CONSTRAINT fk_eventgroupattendees_event FOREIGN KEY (event_id)
        REFERENCES event (id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_eventgroupattendees_group FOREIGN KEY (group_id)
        REFERENCES groups (id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

-- Event series table
CREATE TABLE eventseries
(
    id       BIGSERIAL PRIMARY KEY,
    eventid  BIGINT NOT NULL,
    seriesid BIGINT NOT NULL
);

-- Event user attendees table
CREATE TABLE eventuserattendees
(
    id       BIGSERIAL PRIMARY KEY,
    role     SMALLINT,
    event_id BIGINT,
    user_id  BIGINT,
    CONSTRAINT fk_eventuserattendees_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_eventuserattendees_event FOREIGN KEY (event_id)
        REFERENCES event (id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

-- Groups-Users junction table (many-to-many)
CREATE TABLE groups_users
(
    group_id   BIGINT NOT NULL,
    members_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, members_id),
    CONSTRAINT fk_groups_users_member FOREIGN KEY (members_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_groups_users_group FOREIGN KEY (group_id)
        REFERENCES groups (id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

-- Message table
CREATE TABLE message
(
    id        BIGSERIAL PRIMARY KEY,
    message   VARCHAR(255),
    timestamp TIMESTAMP(6) WITHOUT TIME ZONE,
    title     VARCHAR(255),
    event_id  BIGINT,
    sender_id BIGINT,
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_message_event FOREIGN KEY (event_id)
        REFERENCES event (id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

-- Push subscription table
CREATE TABLE pushsubscription
(
    id       BIGSERIAL PRIMARY KEY,
    auth     VARCHAR(255) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    p256dh   VARCHAR(255) NOT NULL,
    user_id  BIGINT       NOT NULL,
    CONSTRAINT fk_pushsubscription_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

-- Settings table
CREATE TABLE settings
(
    id                                   BIGSERIAL PRIMARY KEY,
    attendancestatuschangednotifications BOOLEAN NOT NULL,
    contactsnotifications                BOOLEAN NOT NULL,
    eventchangednotifications            BOOLEAN NOT NULL,
    eventremindersnotifications          BOOLEAN NOT NULL,
    groupmembershipnotifications         BOOLEAN NOT NULL,
    messagesnotifications                BOOLEAN NOT NULL,
    user_id                              BIGINT,
    CONSTRAINT fk_settings_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE NO ACTION ON UPDATE NO ACTION
);

-- Indexes
CREATE UNIQUE INDEX uk3g6qnpteuj5qclay1c6a83ey8 ON settings USING btree (user_id);

-- Additional recommended indexes for performance
CREATE INDEX idx_attendance_user ON attendance (user_id);
CREATE INDEX idx_attendance_event ON attendance (event_id);
CREATE INDEX idx_contact_user ON contact (user_id);
CREATE INDEX idx_contact_contact ON contact (contact_id);
CREATE INDEX idx_event_starttime ON event (starttime);
CREATE INDEX idx_event_eventstatus ON event (eventstatus);
CREATE INDEX idx_eventgroupattendees_event ON eventgroupattendees (event_id);
CREATE INDEX idx_eventgroupattendees_group ON eventgroupattendees (group_id);
CREATE INDEX idx_eventuserattendees_event ON eventuserattendees (event_id);
CREATE INDEX idx_eventuserattendees_user ON eventuserattendees (user_id);
CREATE INDEX idx_groups_owner ON groups (owner_id);
CREATE INDEX idx_message_event ON message (event_id);
CREATE INDEX idx_message_sender ON message (sender_id);
CREATE INDEX idx_pushsubscription_user ON pushsubscription (user_id);