-- NDA: позиция под соглашением о неразглашении, показывается бейджем на карточке.
ALTER TABLE custom_product_items ADD COLUMN nda BOOLEAN NOT NULL DEFAULT FALSE;
