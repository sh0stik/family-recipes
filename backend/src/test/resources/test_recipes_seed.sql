-- Test-only seed: 5 generic recipes owned by the test `family` account (test_user_seed.sql).
-- Uses the units, ingredients and categories from the main reference seeds, so it must run after
-- them. One recipe (Медовик) is trashed, so 4 are visible in the `recipes` query.

-- Recipe 1: Блины
INSERT INTO recipes (id, name, description, is_public, is_trashed, user_id)
SELECT gen_random_uuid(), 'Блины', 'Классические тонкие блины на молоке.', true, false, id FROM users WHERE username = 'family'
ON CONFLICT DO NOTHING;

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 2, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Блины' AND i.name = 'яйцо' AND u.abbreviation = 'шт'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 500, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Блины' AND i.name = 'молоко' AND u.abbreviation = 'мл'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 200, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Блины' AND i.name = 'мука' AND u.abbreviation = 'г'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 2, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Блины' AND i.name = 'сахар' AND u.abbreviation = 'ст. л.'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

-- Recipe 2: Омлет
INSERT INTO recipes (id, name, description, is_public, is_trashed, user_id)
SELECT gen_random_uuid(), 'Омлет', 'Пышный омлет на сковороде с молоком.', true, false, id FROM users WHERE username = 'family'
ON CONFLICT DO NOTHING;

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 3, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Омлет' AND i.name = 'яйцо' AND u.abbreviation = 'шт'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 100, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Омлет' AND i.name = 'молоко' AND u.abbreviation = 'мл'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 20, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Омлет' AND i.name = 'масло сливочное' AND u.abbreviation = 'г'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

-- Recipe 3: Салат «Цезарь»
INSERT INTO recipes (id, name, description, is_public, is_trashed, user_id)
SELECT gen_random_uuid(), 'Салат «Цезарь»', 'Куриная грудка, гренки, сыр и соус.', false, false, id FROM users WHERE username = 'family'
ON CONFLICT DO NOTHING;

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 300, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Салат «Цезарь»' AND i.name = 'куриная грудка' AND u.abbreviation = 'г'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 100, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Салат «Цезарь»' AND i.name = 'сыр' AND u.abbreviation = 'г'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 50, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Салат «Цезарь»' AND i.name = 'гренки' AND u.abbreviation = 'г'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

-- Recipe 4: Картошка с грибами
INSERT INTO recipes (id, name, description, is_public, is_trashed, user_id)
SELECT gen_random_uuid(), 'Картошка с грибами', 'Жареная картошка с шампиньонами и луком.', true, false, id FROM users WHERE username = 'family'
ON CONFLICT DO NOTHING;

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 500, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Картошка с грибами' AND i.name = 'картофель' AND u.abbreviation = 'г'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 300, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Картошка с грибами' AND i.name = 'шампиньоны' AND u.abbreviation = 'г'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 1, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Картошка с грибами' AND i.name = 'лук репчатый' AND u.abbreviation = 'шт'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

-- Recipe 5: Торт «Медовик» (trashed — for testing trash screen)
INSERT INTO recipes (id, name, description, is_public, is_trashed, user_id)
SELECT gen_random_uuid(), 'Торт «Медовик»', 'Медовые коржи с кремом из сметаны.', true, true, id FROM users WHERE username = 'family'
ON CONFLICT DO NOTHING;

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 3, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Торт «Медовик»' AND i.name = 'яйцо' AND u.abbreviation = 'шт'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 100, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Торт «Медовик»' AND i.name = 'мед' AND u.abbreviation = 'г'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 400, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Торт «Медовик»' AND i.name = 'сметана' AND u.abbreviation = 'г'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit_id)
SELECT gen_random_uuid(), r.id, i.id, 300, u.id
FROM recipes r, ingredients i, units u
WHERE r.name = 'Торт «Медовик»' AND i.name = 'мука' AND u.abbreviation = 'г'
AND r.user_id = (SELECT id FROM users WHERE username = 'family');

-- Category assignments
INSERT INTO recipe_categories (recipe_id, category_id)
SELECT r.id, c.id FROM recipes r, categories c
WHERE r.name = 'Блины' AND c.name = 'Блины и оладьи'
AND r.user_id = (SELECT id FROM users WHERE username = 'family')
ON CONFLICT DO NOTHING;

INSERT INTO recipe_categories (recipe_id, category_id)
SELECT r.id, c.id FROM recipes r, categories c
WHERE r.name = 'Омлет' AND c.name = 'Вторые блюда'
AND r.user_id = (SELECT id FROM users WHERE username = 'family')
ON CONFLICT DO NOTHING;

INSERT INTO recipe_categories (recipe_id, category_id)
SELECT r.id, c.id FROM recipes r, categories c
WHERE r.name = 'Салат «Цезарь»' AND c.name = 'Салаты'
AND r.user_id = (SELECT id FROM users WHERE username = 'family')
ON CONFLICT DO NOTHING;

INSERT INTO recipe_categories (recipe_id, category_id)
SELECT r.id, c.id FROM recipes r, categories c
WHERE r.name = 'Картошка с грибами' AND c.name = 'Вторые блюда'
AND r.user_id = (SELECT id FROM users WHERE username = 'family')
ON CONFLICT DO NOTHING;

INSERT INTO recipe_categories (recipe_id, category_id)
SELECT r.id, c.id FROM recipes r, categories c
WHERE r.name = 'Торт «Медовик»' AND c.name = 'Торты'
AND r.user_id = (SELECT id FROM users WHERE username = 'family')
ON CONFLICT DO NOTHING;
