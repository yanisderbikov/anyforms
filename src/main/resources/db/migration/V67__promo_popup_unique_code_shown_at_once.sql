ALTER TABLE promo_popup DROP CONSTRAINT promo_popup_type_fields_check;
ALTER TABLE promo_popup ADD CONSTRAINT promo_popup_type_fields_check CHECK (
    (popup_type = 'CONTACT'
        AND success_title IS NOT NULL
        AND discount_percent IS NOT NULL
        AND code_prefix IS NOT NULL
        AND code_ttl_days IS NOT NULL
        AND amo_responsible_user_id IS NOT NULL
        AND amo_task_type_id IS NOT NULL)
    OR (popup_type = 'UNIQUE_CODE'
        AND discount_percent IS NOT NULL
        AND code_prefix IS NOT NULL
        AND code_ttl_days IS NOT NULL)
    OR (popup_type = 'PUBLIC_CODE' AND promo_code_id IS NOT NULL)
);

UPDATE promo_popup SET success_title = NULL, success_text = NULL WHERE popup_type = 'UNIQUE_CODE';
