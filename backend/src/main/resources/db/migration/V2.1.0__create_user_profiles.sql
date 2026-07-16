-- V2.1.0: Create user_profiles cache table, seed from users, then drop users.
--
-- user_profiles is a local cache of Keycloak identity data.
-- It is written through on every authenticated request (JWT claims → row upsert)
-- and used for all profile reads (participant lists, group members, etc.)
-- to avoid runtime Keycloak Admin API calls.

CREATE SEQUENCE IF NOT EXISTS user_profiles_SEQ START WITH 1 INCREMENT BY 50;

CREATE TABLE user_profiles (
    id           BIGSERIAL PRIMARY KEY,
    oidc_id      VARCHAR(255) NOT NULL,
    first_name   VARCHAR(255),
    last_name    VARCHAR(255),
    email        VARCHAR(255),
    profile_picture_url VARCHAR(512),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_profiles_oidc_id UNIQUE (oidc_id)
);

CREATE INDEX idx_user_profiles_oidc_id ON user_profiles (oidc_id);
CREATE INDEX idx_user_profiles_name ON user_profiles (lower(first_name || ' ' || last_name));
CREATE INDEX idx_user_profiles_email ON user_profiles (lower(email));

-- Seed from the users table (initial cache state from existing data)
INSERT INTO user_profiles (oidc_id, first_name, last_name, email, updated_at)
SELECT oidcid, firstname, lastname, email, NOW()
FROM users
ON CONFLICT (oidc_id) DO NOTHING;

-- Drop the users table now that all FK columns have been migrated (V2.0.0)
-- and the profile data has been preserved in user_profiles
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS message_backup;
DROP TABLE IF EXISTS settings_backup;
