-- Seed category names (generic reference data)

INSERT INTO categories (id, name) VALUES
(gen_random_uuid(), 'Торты'),
(gen_random_uuid(), 'Выпечка'),
(gen_random_uuid(), 'Десерты'),
(gen_random_uuid(), 'Блины и оладьи'),
(gen_random_uuid(), 'Заготовки'),
(gen_random_uuid(), 'Салаты'),
(gen_random_uuid(), 'Закуски'),
(gen_random_uuid(), 'Соусы'),
(gen_random_uuid(), 'Вторые блюда'),
(gen_random_uuid(), 'Супы'),
(gen_random_uuid(), 'Каши'),
(gen_random_uuid(), 'Кремы')
ON CONFLICT (name) DO NOTHING;
