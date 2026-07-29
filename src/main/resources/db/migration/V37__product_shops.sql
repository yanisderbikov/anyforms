-- Товар может продаваться сразу в нескольких магазинах: связь product—shop становится many-to-many.
-- Существующий владелец (product.shop_id) переносится в product_shop, колонка удаляется.
CREATE TABLE product_shop
(
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    shop_id    UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, shop_id)
);

CREATE INDEX idx_product_shop_shop_id ON product_shop (shop_id);

INSERT INTO product_shop (product_id, shop_id)
SELECT id, shop_id
FROM product
WHERE shop_id IS NOT NULL;

ALTER TABLE product
    DROP COLUMN shop_id;
