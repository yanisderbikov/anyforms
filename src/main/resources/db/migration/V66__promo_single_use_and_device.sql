ALTER TABLE promo_popup DROP CONSTRAINT promo_popup_type_check;
ALTER TABLE promo_popup ADD CONSTRAINT promo_popup_type_check
    CHECK (popup_type IN ('CONTACT', 'UNIQUE_CODE', 'PUBLIC_CODE'));

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
        AND success_title IS NOT NULL
        AND discount_percent IS NOT NULL
        AND code_prefix IS NOT NULL
        AND code_ttl_days IS NOT NULL)
    OR (popup_type = 'PUBLIC_CODE' AND promo_code_id IS NOT NULL)
);

ALTER TABLE promo_popup ADD COLUMN hide_for_known_contacts BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE promo_code ADD COLUMN max_uses INTEGER CHECK (max_uses > 0);
ALTER TABLE promo_code ADD COLUMN owner_device_id VARCHAR(64);
UPDATE promo_code SET max_uses = 1 WHERE popup_id IS NOT NULL;

ALTER TABLE promo_popup_lead ADD COLUMN device_id VARCHAR(64);
ALTER TABLE promo_popup_lead ALTER COLUMN email DROP NOT NULL;
ALTER TABLE promo_popup_lead ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE promo_popup_lead ALTER COLUMN phone_last10 DROP NOT NULL;
ALTER TABLE promo_popup_lead ALTER COLUMN consent_personal_data DROP NOT NULL;
ALTER TABLE promo_popup_lead ALTER COLUMN consent_advertising DROP NOT NULL;
ALTER TABLE promo_popup_lead ALTER COLUMN consent_version DROP NOT NULL;
ALTER TABLE promo_popup_lead ADD CONSTRAINT promo_popup_lead_client_check
    CHECK (email IS NOT NULL OR phone_last10 IS NOT NULL OR device_id IS NOT NULL);
CREATE INDEX idx_promo_popup_lead_popup_device ON promo_popup_lead (popup_id, device_id);

CREATE TABLE promo_popup_view (
    id         UUID PRIMARY KEY,
    popup_id   UUID        NOT NULL REFERENCES promo_popup (id) ON DELETE CASCADE,
    device_id  VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_promo_popup_view_popup_device ON promo_popup_view (popup_id, device_id, created_at DESC);

ALTER TABLE orders ADD COLUMN device_id VARCHAR(64);
CREATE INDEX idx_orders_device_id ON orders (device_id);
