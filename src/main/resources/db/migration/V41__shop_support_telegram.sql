-- Телеграм-бот поддержки магазина (username без @): письма и витрина ведут
-- покупателя в поддержку того магазина, где он покупал, а не в общий AnyFormsBot.
ALTER TABLE shop
    ADD COLUMN support_telegram VARCHAR(64) NOT NULL DEFAULT 'AnyFormsBot';

UPDATE shop
SET support_telegram = 'afPastrySupportBot'
WHERE slug = 'af_pastry';
