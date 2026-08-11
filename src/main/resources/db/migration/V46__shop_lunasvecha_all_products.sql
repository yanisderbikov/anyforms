-- Витрина lunasvecha стартует с полным каталогом: привязываем все существующие
-- товары к магазину (товары уже привязанные — пропускаются). Дальше состав
-- управляется из админки (shopSlugs у товара); новые товары в магазин
-- автоматически не попадают.
INSERT INTO product_shop (product_id, shop_id)
SELECT p.id, s.id
FROM product p
         JOIN shop s ON s.slug = 'lunasvecha'
ON CONFLICT DO NOTHING;
