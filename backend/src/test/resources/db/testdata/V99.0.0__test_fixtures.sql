-- Test fixtures: pre-insert rows referenced by hardcoded IDs in @BeforeEach / test helpers.
-- Also advance all Hibernate sequences past the fixture IDs to avoid PK conflicts.
-- Note: the users table has been removed; user identity is now sourced from Keycloak (OIDC).

INSERT INTO groups (id, groupname, team_id) VALUES (3, 'Test Group', 1) ON CONFLICT DO NOTHING;
INSERT INTO groups (id, groupname, team_id) VALUES (10, 'Test Group 10', 1) ON CONFLICT DO NOTHING;

INSERT INTO appointment (id, name) VALUES (10, 'Test Appointment') ON CONFLICT DO NOTHING;

-- Advance sequences so Hibernate-generated IDs never collide with the fixture IDs above.
SELECT setval('groups_seq', 1000, false);
SELECT setval('appointment_seq', 1000, false);
SELECT setval('appointment_group_participations_seq', 1000, false);
SELECT setval('appointment_participation_seq', 1000, false);
SELECT setval('friendship_requests_seq', 1000, false);
SELECT setval('group_member_seq', 1000, false);
SELECT setval('message_seq', 1000, false);
SELECT setval('pushsubscription_seq', 1000, false);
SELECT setval('settings_seq', 1000, false);
SELECT setval('push_notification_log_seq', 1000, false);
SELECT setval('team_seq', 1000, false);
SELECT setval('team_member_seq', 1000, false);
SELECT setval('team_invite_seq', 1000, false);
