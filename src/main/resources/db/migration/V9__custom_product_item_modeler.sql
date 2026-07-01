-- Кто моделирует позицию: свободный текст, значения переиспользуются как select с автодобавлением.
ALTER TABLE custom_product_items ADD COLUMN modeler VARCHAR(255);
