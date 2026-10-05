CREATE TABLE promo_popup (
    id                        UUID PRIMARY KEY,
    name                      VARCHAR(128) NOT NULL,
    active                    BOOLEAN      NOT NULL DEFAULT FALSE,
    priority                  INTEGER      NOT NULL DEFAULT 0,
    shop_slug                 VARCHAR(64)  NOT NULL DEFAULT 'anyforms',
    title                     VARCHAR(200) NOT NULL,
    description               TEXT,
    button_text               VARCHAR(64)  NOT NULL,
    success_title             VARCHAR(200) NOT NULL,
    success_text              TEXT,
    delay_seconds             INTEGER      NOT NULL DEFAULT 15 CHECK (delay_seconds BETWEEN 0 AND 3600),
    repeat_after_days         INTEGER      NOT NULL DEFAULT 1 CHECK (repeat_after_days BETWEEN 0 AND 365),
    valid_from                TIMESTAMP(6) WITH TIME ZONE,
    valid_until               TIMESTAMP(6) WITH TIME ZONE,
    discount_percent          INTEGER      NOT NULL CHECK (discount_percent BETWEEN 0 AND 100),
    discount_amount_kopecks   BIGINT CHECK (discount_amount_kopecks > 0),
    min_order_kopecks         BIGINT CHECK (min_order_kopecks > 0),
    code_prefix               VARCHAR(16)  NOT NULL,
    code_ttl_days             INTEGER      NOT NULL CHECK (code_ttl_days BETWEEN 1 AND 365),
    first_order_only          BOOLEAN      NOT NULL DEFAULT TRUE,
    amo_responsible_user_id   BIGINT       NOT NULL,
    amo_task_type_id          BIGINT       NOT NULL,
    amo_task_deadline_minutes INTEGER      NOT NULL DEFAULT 60 CHECK (amo_task_deadline_minutes > 0),
    created_at                TIMESTAMP(6) WITH TIME ZONE,
    updated_at                TIMESTAMP(6) WITH TIME ZONE
);

ALTER TABLE promo_code ADD COLUMN popup_id UUID REFERENCES promo_popup (id) ON DELETE SET NULL;
ALTER TABLE promo_code ADD COLUMN shop_slug VARCHAR(64);
ALTER TABLE promo_code ADD COLUMN owner_email VARCHAR(255);
ALTER TABLE promo_code ADD COLUMN owner_phone_last10 VARCHAR(10);
ALTER TABLE promo_code ADD COLUMN first_order_only BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_promo_code_popup_id ON promo_code (popup_id);

CREATE TABLE promo_popup_lead (
    id                    UUID PRIMARY KEY,
    popup_id              UUID         NOT NULL REFERENCES promo_popup (id),
    promo_code_id         UUID REFERENCES promo_code (id) ON DELETE SET NULL,
    code                  VARCHAR(64)  NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    phone                 VARCHAR(32)  NOT NULL,
    phone_last10          VARCHAR(10)  NOT NULL,
    shop_slug             VARCHAR(64)  NOT NULL,
    consent_personal_data BOOLEAN      NOT NULL,
    consent_advertising   BOOLEAN      NOT NULL,
    consent_version       VARCHAR(32)  NOT NULL,
    ip                    VARCHAR(64),
    user_agent            VARCHAR(512),
    page_url              VARCHAR(1024),
    utm_source            VARCHAR(255),
    utm_medium            VARCHAR(255),
    utm_campaign          VARCHAR(255),
    utm_content           VARCHAR(255),
    utm_term              VARCHAR(255),
    amo_lead_id           BIGINT,
    created_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_promo_popup_lead_popup_email ON promo_popup_lead (popup_id, lower(email));
CREATE INDEX idx_promo_popup_lead_popup_phone ON promo_popup_lead (popup_id, phone_last10);
CREATE INDEX idx_promo_popup_lead_created_at ON promo_popup_lead (created_at DESC);
