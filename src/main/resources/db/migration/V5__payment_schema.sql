-- ============================================================================
--  V5 — схема оплаты: каталог продуктов, транзакции и очередь фоновых тасок.
--    payment_product     — каталог (цена в копейках + путь страницы успеха);
--    payment_transaction — платежи Юкассы;
--    task                — очередь тасок (письма и т.п.), разбирается раннерами.
--  Instant-колонки → TIMESTAMP WITH TIME ZONE (как schedule.time_utc в V2).
-- ============================================================================

CREATE TABLE payment_product (
    id               UUID PRIMARY KEY,
    code             VARCHAR(255) NOT NULL UNIQUE,
    title            VARCHAR(255) NOT NULL,
    description      TEXT NOT NULL,
    price_kopecks    BIGINT NOT NULL,
    vat_code         INTEGER NOT NULL,
    success_url_path VARCHAR(255) NOT NULL,
    active           BOOLEAN NOT NULL,
    created_at       TIMESTAMP(6) WITH TIME ZONE,
    updated_at       TIMESTAMP(6) WITH TIME ZONE
);

CREATE TABLE payment_transaction (
    id                  UUID PRIMARY KEY,
    status              VARCHAR(255) NOT NULL,
    external_payment_id UUID NOT NULL UNIQUE,
    product_code        VARCHAR(255) NOT NULL,
    amount              BIGINT NOT NULL,
    currency            VARCHAR(255) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    marketing_consent   BOOLEAN,
    description         VARCHAR(255),
    created_at          TIMESTAMP(6) WITH TIME ZONE,
    updated_at          TIMESTAMP(6) WITH TIME ZONE
);

CREATE TABLE task (
    id         UUID PRIMARY KEY,
    type       VARCHAR(255) NOT NULL,
    payload    TEXT,
    status     VARCHAR(255) NOT NULL,
    comment    TEXT,
    created_at TIMESTAMP(6) WITH TIME ZONE
);

-- Сид каталога: гайд (990 ₽) и курс (9900 ₽). Цена в копейках.
INSERT INTO payment_product (id, code, title, description, price_kopecks, vat_code, success_url_path, active)
VALUES
    (gen_random_uuid(), 'GUIDE',  'Гайд', 'Гайд anyforms',  99000,  1, '/guide/success',  TRUE),
    (gen_random_uuid(), 'COURSE', 'Курс', 'Курс anyforms', 990000,  1, '/course/success', TRUE);
