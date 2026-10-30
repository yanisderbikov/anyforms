UPDATE payment_product
SET price_kopecks          = 1690000,
    preorder_price_kopecks = NULL,
    preorder_until         = NULL,
    updated_at             = now()
WHERE code = 'COURSE_STREAM_2';
