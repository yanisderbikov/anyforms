ALTER TABLE payment_transaction
    ADD COLUMN payment_url TEXT,
    ADD COLUMN contact_name VARCHAR(255),
    ADD COLUMN contact_phone VARCHAR(64);

ALTER TABLE payment_transaction
    ALTER COLUMN email DROP NOT NULL;
