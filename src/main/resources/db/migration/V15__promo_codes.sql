-- ============================================================================
--  V15 — промокоды: каталог кодов со скидкой в процентах и сроком действия,
--  плюс атрибуция платежа (промокод и UTM-метки) в payment_transaction.
--  Код хранится в верхнем регистре, сравнение — по нормализованному значению.
-- ============================================================================

CREATE TABLE promo_code (
    id               UUID PRIMARY KEY,
    code             VARCHAR(64) NOT NULL UNIQUE,
    discount_percent INTEGER NOT NULL CHECK (discount_percent BETWEEN 1 AND 100),
    active           BOOLEAN NOT NULL,
    valid_from       TIMESTAMP(6) WITH TIME ZONE,
    valid_until      TIMESTAMP(6) WITH TIME ZONE,
    created_at       TIMESTAMP(6) WITH TIME ZONE,
    updated_at       TIMESTAMP(6) WITH TIME ZONE
);

ALTER TABLE payment_transaction ADD COLUMN promo_code VARCHAR(64);
ALTER TABLE payment_transaction ADD COLUMN discount_percent INTEGER;
ALTER TABLE payment_transaction ADD COLUMN utm_source VARCHAR(255);
ALTER TABLE payment_transaction ADD COLUMN utm_medium VARCHAR(255);

-- Сид: «ГАЙД» — скидка 35%, действует до конца июля 2026 (МСК).
INSERT INTO promo_code (id, code, discount_percent, active, valid_from, valid_until, created_at, updated_at)
VALUES (gen_random_uuid(), 'ГАЙД', 35, TRUE, NULL, '2026-08-01 00:00:00+03', now(), now());
