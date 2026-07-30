-- Описание товара давно объявлено как TEXT в @Entity, но в БД на старых окружениях
-- колонка осталась VARCHAR(255): её создал ddl-auto=update, а baseline V4 с
-- CREATE TABLE IF NOT EXISTS был no-op. Hibernate в режиме validate длины не сверяет,
-- поэтому падало только на вставке длинного описания.
ALTER TABLE product
    ALTER COLUMN description TYPE TEXT;
