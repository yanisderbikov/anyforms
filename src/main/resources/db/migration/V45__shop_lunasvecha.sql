-- Новый партнёрский магазин lunasvecha (свечи ручной работы, обучение свечеварению):
-- витрина /shop/lunasvecha. Поддержка — свой телеграм-бот LunaSvechaSupportBot
-- (письма и витрина ведут в него).
INSERT INTO shop (id, slug, name, active, support_telegram, created_at)
VALUES (gen_random_uuid(), 'lunasvecha', 'lunasvecha', TRUE, 'LunaSvechaSupportBot', now());
