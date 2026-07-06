-- ============================================================================
--  V11 — order-first: заказ создаётся сразу при чекауте.
--    Заказ маркетплейса заводится со статусом AWAITING_PAYMENT в момент оформления,
--    вебхук Юкассы переводит его в PAID (или CANCELED при отмене платежа).
--    Снапшот чекаута из V10 (payment_transaction_item + колонки клиента/ПВЗ на
--    транзакции) больше не нужен: состав, контакты и ПВЗ живут в самом заказе.
--      orders.payment_status              — NONE | AWAITING_PAYMENT | PAID | CANCELED;
--      custom_product_items.price_kopecks — цена позиции на момент оплаты (для чека);
--      payment_transaction.order_id       — связь платежа с заказом.
--    Рабочие списки цеха показывают только NONE/PAID — брошенные корзины не попадают.
-- ============================================================================

ALTER TABLE orders
    ADD COLUMN payment_status VARCHAR(32) NOT NULL DEFAULT 'NONE';

ALTER TABLE custom_product_items
    ADD COLUMN price_kopecks BIGINT;

ALTER TABLE payment_transaction
    ADD COLUMN order_id BIGINT REFERENCES orders (id);

-- Демонтаж снапшота V10.
DROP TABLE payment_transaction_item;

ALTER TABLE payment_transaction
    DROP COLUMN customer_name,
    DROP COLUMN customer_phone,
    DROP COLUMN pvz_city,
    DROP COLUMN pvz_street,
    DROP COLUMN pvz_code;

CREATE INDEX idx_payment_transaction_order_id ON payment_transaction (order_id);
CREATE INDEX idx_orders_payment_status ON orders (payment_status);
