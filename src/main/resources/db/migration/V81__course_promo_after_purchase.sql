ALTER TABLE promo_code ADD COLUMN product_code VARCHAR(64);
ALTER TABLE promo_popup ADD COLUMN product_code VARCHAR(64);
ALTER TABLE promo_popup ALTER COLUMN popup_type TYPE VARCHAR(32);

ALTER TABLE promo_popup DROP CONSTRAINT promo_popup_type_check;
ALTER TABLE promo_popup ADD CONSTRAINT promo_popup_type_check
    CHECK (popup_type IN ('CONTACT', 'UNIQUE_CODE', 'PUBLIC_CODE', 'AFTER_PURCHASE', 'COURSE_AFTER_PURCHASE'));

ALTER TABLE promo_popup DROP CONSTRAINT promo_popup_type_fields_check;
ALTER TABLE promo_popup ADD CONSTRAINT promo_popup_type_fields_check CHECK (
    (popup_type = 'CONTACT'
        AND success_title IS NOT NULL
        AND discount_percent IS NOT NULL
        AND code_prefix IS NOT NULL
        AND code_ttl_days IS NOT NULL
        AND amo_responsible_user_id IS NOT NULL
        AND amo_task_type_id IS NOT NULL)
    OR (popup_type IN ('UNIQUE_CODE', 'AFTER_PURCHASE')
        AND discount_percent IS NOT NULL
        AND code_prefix IS NOT NULL
        AND code_ttl_days IS NOT NULL)
    OR (popup_type = 'COURSE_AFTER_PURCHASE'
        AND product_code IS NOT NULL
        AND discount_percent IS NOT NULL
        AND code_prefix IS NOT NULL
        AND code_ttl_days IS NOT NULL)
    OR (popup_type = 'PUBLIC_CODE' AND promo_code_id IS NOT NULL)
);

INSERT INTO promo_popup (id, name, popup_type, active, priority, shop_slug, title, description, button_text,
                         delay_seconds, repeat_after_hours, max_shows, hide_for_known_contacts,
                         discount_percent, discount_amount_kopecks, min_order_kopecks, code_prefix, code_ttl_days,
                         first_order_only, product_code, amo_task_deadline_minutes, created_at, updated_at)
VALUES ('5c1f3b2e-7d4a-4c6b-9e8f-0a1b2c3d4e5f', 'Скидка на курс после покупки', 'COURSE_AFTER_PURCHASE', TRUE, 0,
        'anyforms', 'скидка {discount} на курс по молдам',
        'Хотите делать такие формы сами? Вернём стоимость этой покупки скидкой на курс по производству силиконовых форм: вместо {oldPrice} курс обойдётся в {price}. Промокод действует {days}.',
        'Открыть курс со скидкой', 0, 0, NULL, FALSE, 0, NULL, NULL, 'COURSE', 90, FALSE, 'COURSE_STREAM_2', 60,
        now(), now());
