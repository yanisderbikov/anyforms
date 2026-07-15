-- Новый статус оплаты REFUNDED: отделяем возвраты денег от неуспешных оплат.
-- orders.payment_status — NONE | AWAITING_PAYMENT | PAID | CANCELED | REFUNDED
-- (колонка VARCHAR без check-констрейнта, схему менять не нужно).
--
-- Исторические данные: заказ с payment_status='CANCELED', у которого заполнен
-- purchase_date, был оплачен и отменён позже — это возврат, а не неуспешная
-- оплата (purchase_date проставляется только при переводе заказа в PAID).
UPDATE orders
SET payment_status = 'REFUNDED'
WHERE payment_status = 'CANCELED'
  AND purchase_date IS NOT NULL;
