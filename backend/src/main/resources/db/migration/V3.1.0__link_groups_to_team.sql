-- V3.1.0: Link groups to a team
--
-- Groups are now scoped to a team. All existing groups are assigned to
-- the single default team that was seeded in V3.0.0.
-- Group creation requires a teamId from this version on.

ALTER TABLE groups ADD COLUMN team_id BIGINT;

UPDATE groups
SET team_id = (SELECT id FROM team ORDER BY id LIMIT 1)
WHERE team_id IS NULL;

ALTER TABLE groups ALTER COLUMN team_id SET NOT NULL;

ALTER TABLE groups
    ADD CONSTRAINT fk_groups_team
    FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE CASCADE;

CREATE INDEX idx_groups_team ON groups (team_id);
