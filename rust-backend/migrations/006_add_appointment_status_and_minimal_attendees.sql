-- Add status and minimal_attendees columns to appointments table
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'PLANNED';
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS minimal_attendees INTEGER;

-- Add constraint to ensure minimal_attendees is non-negative if set
ALTER TABLE appointments ADD CONSTRAINT check_minimal_attendees_non_negative CHECK (minimal_attendees IS NULL OR minimal_attendees >= 0);

-- Add index on status for filtering deleted/cancelled appointments
CREATE INDEX IF NOT EXISTS idx_appointments_status ON appointments(status);
