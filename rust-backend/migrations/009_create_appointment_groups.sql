-- Create appointment_groups table to track group participation in appointments
CREATE TABLE IF NOT EXISTS appointment_groups (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL,
    group_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ATTENDANT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_appointment_groups_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_groups_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT unique_appointment_group UNIQUE(appointment_id, group_id)
);

CREATE INDEX IF NOT EXISTS idx_appointment_groups_appointment_id ON appointment_groups(appointment_id);
CREATE INDEX IF NOT EXISTS idx_appointment_groups_group_id ON appointment_groups(group_id);
CREATE INDEX IF NOT EXISTS idx_appointment_groups_deleted_at ON appointment_groups(deleted_at);
