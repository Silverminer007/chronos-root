-- V1.9.0: Drop temporary backup tables created by V1.1.0
--
-- V1.1.0 created message_backup and settings_backup as safety snapshots
-- before restructuring those tables. The drops were intentionally left
-- commented out in V1.1.0 to allow manual rollback if needed.
-- The V1.1.0 migration has been stable in production; these backups
-- are no longer needed.

DROP TABLE IF EXISTS message_backup;
DROP TABLE IF EXISTS settings_backup;