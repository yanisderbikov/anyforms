-- Публичные номера теперь у всех заказов (включая розничные АМО-заказы):
-- бэкфилл оставшихся orders без public_id.

DO $$
DECLARE
    row_id BIGINT;
    pid TEXT;
BEGIN
    FOR row_id IN SELECT id FROM orders WHERE public_id IS NULL LOOP
        LOOP
            SELECT string_agg(substr('ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', (floor(random() * 36))::int + 1, 1), '')
            INTO pid
            FROM generate_series(1, 6);
            EXIT WHEN NOT EXISTS (SELECT 1 FROM orders WHERE public_id = pid);
        END LOOP;
        UPDATE orders SET public_id = pid WHERE id = row_id;
    END LOOP;
END $$;
