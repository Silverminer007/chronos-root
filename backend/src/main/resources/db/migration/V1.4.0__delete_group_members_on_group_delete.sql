ALTER TABLE group_member
DROP CONSTRAINT fk_group_member_group,
      ADD CONSTRAINT fk_group_member_group FOREIGN KEY (group_id)
          REFERENCES groups (id) ON DELETE CASCADE ON UPDATE NO ACTION;