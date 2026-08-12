-- ============================================================================
--  V47 — промокоды: фиксированная скидка в рублях поверх процента и порог
--  минимальной суммы заказа. Оба поля необязательные (NULL = не задано).
--  Процент теперь может быть 0 — для чисто «рублёвых» промокодов.
--  В payment_transaction — атрибуция применённой фиксированной скидки,
--  по аналогии с discount_percent из V15.
-- ============================================================================

ALTER TABLE promo_code ADD COLUMN discount_amount_kopecks BIGINT CHECK (discount_amount_kopecks > 0);
ALTER TABLE promo_code ADD COLUMN min_order_kopecks BIGINT CHECK (min_order_kopecks > 0);

ALTER TABLE promo_code DROP CONSTRAINT IF EXISTS promo_code_discount_percent_check;
ALTER TABLE promo_code ADD CONSTRAINT promo_code_discount_percent_check CHECK (discount_percent BETWEEN 0 AND 100);

ALTER TABLE payment_transaction ADD COLUMN discount_amount_kopecks BIGINT;
