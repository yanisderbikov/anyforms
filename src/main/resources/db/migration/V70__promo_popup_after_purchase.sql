ALTER TABLE promo_popup ALTER COLUMN first_order_only SET DEFAULT FALSE;

ALTER TABLE promo_popup DROP CONSTRAINT promo_popup_type_check;
ALTER TABLE promo_popup ADD CONSTRAINT promo_popup_type_check
    CHECK (popup_type IN ('CONTACT', 'UNIQUE_CODE', 'PUBLIC_CODE', 'AFTER_PURCHASE'));

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
    OR (popup_type = 'PUBLIC_CODE' AND promo_code_id IS NOT NULL)
);

ALTER TABLE promo_popup_lead ADD COLUMN order_id BIGINT REFERENCES orders (id) ON DELETE SET NULL;
CREATE INDEX idx_promo_popup_lead_popup_order ON promo_popup_lead (popup_id, order_id);
