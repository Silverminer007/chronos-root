-- V3.0.0: Replace "Freunde" with "Teams" model
--
-- Changes:
--   1. Create team, team_member, team_invite tables
--   2. Seed default team from all existing user_profiles
--   3. Alter groups: drop owner_oidcid, add deleted_at for soft-delete
--   4. friendship_requests is left intact — dropped in V3.1.0 after API removal

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 1: Create new tables
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE team
(
    id         BIGINT PRIMARY KEY NOT NULL,
    name       VARCHAR(255)                NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE   NOT NULL
);

CREATE TABLE team_member
(
    id           BIGINT PRIMARY KEY NOT NULL,
    team_id      BIGINT                      NOT NULL,
    user_oidcid  VARCHAR(255)                NOT NULL,
    role         VARCHAR(20)                 NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    joined_at    TIMESTAMP WITH TIME ZONE   NOT NULL,
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE CASCADE,
    CONSTRAINT uk_team_member UNIQUE (team_id, user_oidcid)
);

CREATE TABLE team_invite
(
    id                 BIGINT PRIMARY KEY NOT NULL,
    team_id            BIGINT                      NOT NULL,
    token              VARCHAR(255)                NOT NULL,
    type               VARCHAR(20)                 NOT NULL CHECK (type IN ('MULTI_USE', 'SINGLE_USE')),
    target_email       VARCHAR(255),
    expires_at         TIMESTAMP WITH TIME ZONE,
    status             VARCHAR(20)                 NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED', 'USED')),
    use_count          INT                         NOT NULL DEFAULT 0,
    created_by_oidcid  VARCHAR(255)                NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE   NOT NULL,
    CONSTRAINT fk_team_invite_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE CASCADE,
    CONSTRAINT uk_team_invite_token UNIQUE (token)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 2: Sequences (Hibernate allocationSize = 50)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE SEQUENCE team_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE team_member_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE team_invite_SEQ START WITH 1 INCREMENT BY 50;

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 3: Indexes
-- ─────────────────────────────────────────────────────────────────────────────

CREATE INDEX idx_team_member_team    ON team_member (team_id);
CREATE INDEX idx_team_member_user    ON team_member (user_oidcid);
CREATE INDEX idx_team_invite_team    ON team_invite (team_id);
CREATE INDEX idx_team_invite_token   ON team_invite (token);
CREATE INDEX idx_team_invite_status  ON team_invite (status);

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 4: Seed default team and migrate existing users (#56)
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO team (id, name, created_at)
VALUES (nextval('team_SEQ'), 'Chronos', NOW());

INSERT INTO team_member (id, team_id, user_oidcid, role, joined_at)
SELECT nextval('team_member_SEQ'),
       (SELECT id FROM team LIMIT 1),
       oidc_id,
       'MEMBER',
       NOW()
FROM user_profiles;

-- First row in user_profiles becomes OWNER (replaces the now-gone "user ID 1")
UPDATE team_member
SET role = 'OWNER'
WHERE user_oidcid = (SELECT oidc_id FROM user_profiles ORDER BY oidc_id LIMIT 1)
  AND team_id     = (SELECT id FROM team LIMIT 1);

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 5: Alter groups table — remove owner, add soft-delete
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE groups
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

DROP INDEX IF EXISTS idx_groups_owner;

ALTER TABLE groups
    DROP COLUMN IF EXISTS owner_oidcid;
