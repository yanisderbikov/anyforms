-- Новый партнёрский магазин di_gips (Диана, творчество из гипса): витрина /shop/di_gips.
-- Поддержка — свой телеграм-бот di_gips_supportbot (письма и витрина ведут в него).
INSERT INTO shop (id, slug, name, active, support_telegram, created_at)
VALUES (gen_random_uuid(), 'di_gips', 'di_gips', TRUE, 'di_gips_supportbot', now());
