CREATE INDEX idx_payment_transaction_promo_code ON payment_transaction (promo_code) WHERE promo_code IS NOT NULL;
