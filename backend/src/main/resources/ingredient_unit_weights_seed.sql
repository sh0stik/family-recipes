-- How much one unit of a given ingredient weighs, in grams.
--
-- Nutrition is computed by converting every ingredient to grams. Only ~25% of ingredient entries
-- were recorded in г/кг, and everything else was dropped, so recipes reported per-100g values
-- derived from a fraction of their contents (chicken with buckwheat reported the profile of plain
-- buckwheat). These rows close that gap for the ingredients already in use.
--
-- Values are cook's approximations, not measurements. They are stored rather than hardcoded so they
-- can be corrected from the app without a redeploy.

-- Units whose weight does not depend on what is measured: safe as a single number.
UPDATE units SET grams_per_unit = 1     WHERE abbreviation IN ('мл', 'ml');
UPDATE units SET grams_per_unit = 1000  WHERE abbreviation IN ('л', 'l');
UPDATE units SET grams_per_unit = 0.5   WHERE abbreviation IN ('щепотка', 'pinch');

-- Everything below varies by ingredient, so it lives per (ingredient, unit).
-- Spoons and glasses: dry goods differ substantially from liquids.
INSERT INTO ingredient_unit_weights (id, ingredient_id, unit_id, grams)
SELECT gen_random_uuid(), i.id, u.id, v.grams
FROM (VALUES
    -- ingredient,             unit,        grams
    ('мука',                   'стакан',    160.0),
    ('мука',                   'ст. л.',     10.0),
    ('мука',                   'ч. л.',       3.0),
    ('сахар',                  'стакан',    200.0),
    ('сахар',                  'ст. л.',     20.0),
    ('сахар',                  'ч. л.',       7.0),
    ('сахарная пудра',         'ст. л.',     12.0),
    ('соль',                   'ст. л.',     25.0),
    ('соль',                   'ч. л.',       7.0),
    ('сода',                   'ч. л.',       6.0),
    ('разрыхлитель',           'ч. л.',       4.0),
    ('крахмал',                'ст. л.',     12.0),
    ('какао',                  'ст. л.',      9.0),
    ('манка',                  'ст. л.',     16.0),
    ('рис',                    'стакан',    180.0),
    ('гречка',                 'стакан',    170.0),
    ('молоко',                 'стакан',    200.0),
    ('вода',                   'стакан',    200.0),
    ('сметана',                'ст. л.',     20.0),
    ('майонез',                'ст. л.',     18.0),
    ('томатная паста',         'ст. л.',     20.0),
    ('мед',                    'ст. л.',     28.0),
    ('масло растительное',     'ст. л.',     17.0),
    ('масло растительное',     'стакан',    190.0),
    ('масло сливочное',        'ст. л.',     20.0),
    ('уксус',                  'ст. л.',     15.0),
    ('соевый соус',            'ст. л.',     16.0),
    -- Countable items: 'шт' is meaningless without knowing the item.
    ('яйцо',                   'шт',         60.0),
    ('яйцо вареное',           'шт',         60.0),
    ('желток',                 'шт',         18.0),
    ('белок',                  'шт',         35.0),
    ('чеснок',                 'шт',          5.0),   -- one clove
    ('лук репчатый',           'шт',        100.0),
    ('морковь',                'шт',         85.0),
    ('картофель',              'шт',        100.0),
    ('помидор',                'шт',        120.0),
    ('помидоры',               'шт',        120.0),
    ('огурец',                 'шт',        100.0),
    ('перец болгарский',       'шт',        150.0),
    ('перец горький',          'шт',         10.0),
    ('лимон',                  'шт',        100.0),
    ('лайм',                   'шт',         70.0),
    ('апельсин',               'шт',        200.0),
    ('яблоки',                 'шт',        150.0),
    ('яблоко',                 'шт',        150.0),
    ('банан',                  'шт',        120.0),
    ('капуста',                'шт',       1500.0),
    ('кабачок',                'шт',        300.0),
    ('баклажан',               'шт',        250.0),
    ('печенье',                'шт',         15.0),
    ('лавровый лист',          'шт',          0.2),
    ('лист смородины',         'шт',          0.5),
    ('укроп',                  'шт',         10.0),
    ('банка',                  'банка',     400.0)
) AS v(ingredient_name, unit_abbr, grams)
JOIN ingredients i ON lower(i.name) = lower(v.ingredient_name)
JOIN units u ON u.abbreviation = v.unit_abbr
ON CONFLICT (ingredient_id, unit_id) DO NOTHING;

-- 'банка' is a container, not an ingredient property, so give it a generic weight for anything
-- without a specific row.
UPDATE units SET grams_per_unit = 400 WHERE abbreviation = 'банка' AND grams_per_unit IS NULL;
