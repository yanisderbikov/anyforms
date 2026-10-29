ALTER TABLE payment_product
    ADD COLUMN preorder_price_kopecks BIGINT,
    ADD COLUMN preorder_until         TIMESTAMPTZ;

UPDATE payment_product
SET active     = FALSE,
    updated_at = now()
WHERE code IN ('COURSE', 'COURSE_PERSONAL');

INSERT INTO payment_product (id, code, title, description, price_kopecks, preorder_price_kopecks, preorder_until,
                             success_url_path, active, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'COURSE_STREAM_2',
    'Курс по производству силиконовых форм — второй поток',
    'Видео-курс anyforms по производству силиконовых форм, второй поток',
    2900000,
    1900000,
    '2026-11-10 00:00:00+03',
    '/course/success',
    TRUE,
    now(),
    now()
);
