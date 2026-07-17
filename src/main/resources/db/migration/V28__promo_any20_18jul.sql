-- Промокод ANY-20-18JUL: скидка 20%, действует до конца субботы 18.07.2026 по Москве.
INSERT INTO promo_code (id, code, discount_percent, active, valid_from, valid_until, created_at, updated_at)
VALUES (gen_random_uuid(), 'ANY-20-18JUL', 20, TRUE, NULL, '2026-07-19 00:00:00+03', now(), now());
