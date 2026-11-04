ALTER TABLE promo_popup ADD COLUMN order_sum_from_kopecks BIGINT;
ALTER TABLE promo_popup ADD COLUMN order_sum_to_kopecks BIGINT;

ALTER TABLE promo_popup ADD CONSTRAINT promo_popup_order_sum_range_check CHECK (
    order_sum_from_kopecks IS NULL
    OR order_sum_to_kopecks IS NULL
    OR order_sum_from_kopecks <= order_sum_to_kopecks
);
