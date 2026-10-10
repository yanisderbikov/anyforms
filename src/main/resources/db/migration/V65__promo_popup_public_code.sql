ALTER TABLE promo_popup ADD COLUMN popup_type VARCHAR(16) NOT NULL DEFAULT 'CONTACT';
ALTER TABLE promo_popup ADD CONSTRAINT promo_popup_type_check CHECK (popup_type IN ('CONTACT', 'PUBLIC_CODE'));

ALTER TABLE promo_popup ADD COLUMN promo_code_id UUID REFERENCES promo_code (id);
CREATE INDEX idx_promo_popup_promo_code_id ON promo_popup (promo_code_id);

ALTER TABLE promo_popup ADD COLUMN max_shows INTEGER CHECK (max_shows BETWEEN 1 AND 1000);

ALTER TABLE promo_popup ADD COLUMN repeat_after_hours INTEGER NOT NULL DEFAULT 24
    CHECK (repeat_after_hours BETWEEN 0 AND 8760);
UPDATE promo_popup SET repeat_after_hours = LEAST(repeat_after_days * 24, 8760);
ALTER TABLE promo_popup DROP COLUMN repeat_after_days;

ALTER TABLE promo_popup ALTER COLUMN success_title DROP NOT NULL;
ALTER TABLE promo_popup ALTER COLUMN discount_percent DROP NOT NULL;
ALTER TABLE promo_popup ALTER COLUMN code_prefix DROP NOT NULL;
ALTER TABLE promo_popup ALTER COLUMN code_ttl_days DROP NOT NULL;
ALTER TABLE promo_popup ALTER COLUMN amo_responsible_user_id DROP NOT NULL;
ALTER TABLE promo_popup ALTER COLUMN amo_task_type_id DROP NOT NULL;

ALTER TABLE promo_popup ADD CONSTRAINT promo_popup_type_fields_check CHECK (
    (popup_type = 'CONTACT'
        AND success_title IS NOT NULL
        AND discount_percent IS NOT NULL
        AND code_prefix IS NOT NULL
        AND code_ttl_days IS NOT NULL
        AND amo_responsible_user_id IS NOT NULL
        AND amo_task_type_id IS NOT NULL)
    OR (popup_type = 'PUBLIC_CODE' AND promo_code_id IS NOT NULL)
);
