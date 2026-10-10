ALTER TABLE orders
    ADD COLUMN email                      VARCHAR(255),
    ADD COLUMN last_delivery_notification VARCHAR(32);
