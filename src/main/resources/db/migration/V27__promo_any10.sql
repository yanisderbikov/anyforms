-- Промокод ANY-10 для маркетплейса: скидка 10%, бессрочный.
INSERT INTO promo_code (id, code, discount_percent, active, valid_from, valid_until, created_at, updated_at)
VALUES (gen_random_uuid(), 'ANY-10', 10, TRUE, NULL, NULL, now(), now());
