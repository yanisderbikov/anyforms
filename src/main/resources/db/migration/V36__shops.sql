-- Магазины витрины: кроме anyforms появляются партнёрские магазины (af_pastry).
-- product.shop_id — чей это товар (владелец), orders.shop_id — с какой витрины товар куплен.
-- Покупка на общей витрине /shop засчитывается магазину anyforms, даже если товар партнёрский.
CREATE TABLE shop
(
    id         UUID PRIMARY KEY,
    slug       VARCHAR(64)  NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

INSERT INTO shop (id, slug, name, active, created_at)
VALUES (gen_random_uuid(), 'anyforms', 'anyforms', TRUE, now()),
       (gen_random_uuid(), 'af_pastry', 'af_pastry', TRUE, now());

ALTER TABLE product
    ADD COLUMN shop_id UUID REFERENCES shop (id);

UPDATE product
SET shop_id = (SELECT id FROM shop WHERE slug = 'anyforms')
WHERE shop_id IS NULL;

ALTER TABLE product
    ALTER COLUMN shop_id SET NOT NULL;

CREATE INDEX idx_product_shop_id ON product (shop_id);

-- Заказы, оформленные до появления магазинов, остаются без витрины (NULL) — это заказы anyforms.
ALTER TABLE orders
    ADD COLUMN shop_id UUID REFERENCES shop (id);

CREATE INDEX idx_orders_shop_id ON orders (shop_id);
