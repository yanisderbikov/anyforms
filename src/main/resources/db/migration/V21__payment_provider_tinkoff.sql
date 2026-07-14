ALTER TABLE payment_transaction
    ALTER COLUMN external_payment_id TYPE VARCHAR(64) USING external_payment_id::text;

ALTER TABLE payment_transaction
    ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT 'YOOKASSA';
