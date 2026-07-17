-- Папка фото стала необязательной: создаётся автоматически (по id товара) при первой загрузке фото из админки.
ALTER TABLE product ALTER COLUMN s3photos_folder_path DROP NOT NULL;
