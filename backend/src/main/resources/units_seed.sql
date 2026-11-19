-- Seed measurement units

INSERT INTO units (id, name, abbreviation, type) VALUES
(gen_random_uuid(), 'грамм', 'г', 'WEIGHT'),
(gen_random_uuid(), 'килограмм', 'кг', 'WEIGHT'),
(gen_random_uuid(), 'миллилитр', 'мл', 'VOLUME'),
(gen_random_uuid(), 'литр', 'л', 'VOLUME'),
(gen_random_uuid(), 'стакан', 'стакан', 'VOLUME'),
(gen_random_uuid(), 'столовая ложка', 'ст. л.', 'VOLUME'),
(gen_random_uuid(), 'чайная ложка', 'ч. л.', 'VOLUME'),
(gen_random_uuid(), 'штука', 'шт', 'COUNT'),
(gen_random_uuid(), 'банка', 'банка', 'COUNT'),
(gen_random_uuid(), 'щепотка', 'щепотка', 'OTHER')
ON CONFLICT (abbreviation) DO NOTHING;
