-- Test-only seed user: the family account with the password "password".
-- Tests log in as it. Only the tests seed it; other environments create accounts through the app
-- (or load the demo data in demo/).

INSERT INTO users (id, username, email, password, role, preferred_locale)
VALUES (gen_random_uuid(), 'family', 'family@recipes.local',
        '$2a$10$DoMCase.5/OvwFIq/VKUNOh/DQ.j/uwa7vFq5d2gqkZhwO01iBIJ2',
        'USER', 'ru')
ON CONFLICT (username) DO NOTHING;
