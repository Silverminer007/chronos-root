-- V2.2.0: Add creator_oidc_id column to appointment table
--
-- This column tracks which user created an appointment, enabling authorization
-- checks for update/delete/cancel operations. Only the creator (or admin) can
-- modify an appointment.

ALTER TABLE appointment ADD COLUMN creator_oidc_id VARCHAR(255);

-- Create index for authorization queries (finding creator's appointments)
CREATE INDEX idx_appointment_creator_oidcid ON appointment (creator_oidc_id);
