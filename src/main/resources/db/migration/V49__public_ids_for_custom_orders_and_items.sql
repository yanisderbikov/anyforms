-- Публичные номера для под-заказов и их позиций:
--   orders.public_id — бэкфилл для всех под-заказных сделок (retail = FALSE);
--   custom_product_items.public_id — новая колонка + бэкфилл, для публичных ссылок вместо внутреннего id.

ALTER TABLE custom_product_items
    ADD COLUMN public_id VARCHAR(6);

DO $$
DECLARE
    row_id BIGINT;
    pid TEXT;
BEGIN
    FOR row_id IN SELECT id FROM orders WHERE retail = FALSE AND public_id IS NULL LOOP
        LOOP
            SELECT string_agg(substr('ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', (floor(random() * 36))::int + 1, 1), '')
            INTO pid
            FROM generate_series(1, 6);
            EXIT WHEN NOT EXISTS (SELECT 1 FROM orders WHERE public_id = pid);
        END LOOP;
        UPDATE orders SET public_id = pid WHERE id = row_id;
    END LOOP;

    FOR row_id IN SELECT id FROM custom_product_items WHERE public_id IS NULL LOOP
        LOOP
            SELECT string_agg(substr('ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', (floor(random() * 36))::int + 1, 1), '')
            INTO pid
            FROM generate_series(1, 6);
            EXIT WHEN NOT EXISTS (SELECT 1 FROM custom_product_items WHERE public_id = pid);
        END LOOP;
        UPDATE custom_product_items SET public_id = pid WHERE id = row_id;
    END LOOP;
END $$;

CREATE UNIQUE INDEX idx_custom_product_items_public_id ON custom_product_items (public_id);
