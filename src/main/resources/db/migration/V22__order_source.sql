-- Происхождение заказа: у MARKETPLACE-заказов источник правды — бек,
-- синк из АМО не трогает retail/items/контакт/ПВЗ/дату оплаты.
-- Дефолта у колонки нет: каждая точка создания заказа проставляет source явно.
ALTER TABLE orders ADD COLUMN source VARCHAR(32);

-- public_id есть только у заказов, созданных маркетплейсом.
UPDATE orders SET source = 'MARKETPLACE' WHERE public_id IS NOT NULL;

-- Кастомные заказы («под заказ») распознаём по наличию custom_product_items;
-- по семантике синка CUSTOM эквивалентен AMO, так что это только маркировка.
UPDATE orders SET source = 'CUSTOM'
WHERE public_id IS NULL
  AND id IN (SELECT DISTINCT order_id FROM custom_product_items);

-- Всё остальное исторически рождено синком из АМО.
UPDATE orders SET source = 'AMO' WHERE source IS NULL;

ALTER TABLE orders ALTER COLUMN source SET NOT NULL;

-- Ретро-фикс: оплаченные маркетплейс-заказы — розница по определению,
-- даже если товары не были привязаны к сделке АМО (см. заказ #264).
UPDATE orders SET retail = TRUE
WHERE public_id IS NOT NULL AND payment_status = 'PAID';
