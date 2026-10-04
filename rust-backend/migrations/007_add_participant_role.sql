-- Add role column to appointment_participants table
ALTER TABLE appointment_participants ADD COLUMN IF NOT EXISTS role VARCHAR(50) NOT NULL DEFAULT 'ATTENDANT';

-- Create index on role for filtering by participant role
CREATE INDEX IF NOT EXISTS idx_appointment_participants_role ON appointment_participants(role);
