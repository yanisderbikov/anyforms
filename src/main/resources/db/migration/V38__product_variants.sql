-- Варианты товара (размер/объём) со своей ценой: «80 мл» — 1990 ₽, «50 мл» — 1000 ₽.
-- Товар без вариантов продаётся по основной цене, как раньше. В позицию заказа
-- вариант уходит как «<имя товара> <label>» (например «Лилит 20 см»).
CREATE TABLE product_variant
(
    id           UUID PRIMARY KEY,
    product_id   UUID         NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    label        VARCHAR(255) NOT NULL,
    price        VARCHAR(255) NOT NULL,
    order_number INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_product_variant_product_id ON product_variant (product_id);
