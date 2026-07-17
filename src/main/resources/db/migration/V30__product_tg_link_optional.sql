-- Ссылка на Telegram-пост товара стала необязательной.
ALTER TABLE product ALTER COLUMN tg_link DROP NOT NULL;
