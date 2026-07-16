-- ============================================================================
--  V25 — время последней смены статуса кастомной позиции.
--  Для существующих строк проставляем дату создания, чтобы не было NULL.
-- ============================================================================

ALTER TABLE custom_product_items ADD COLUMN status_updated_at TIMESTAMP;

UPDATE custom_product_items SET status_updated_at = created_at;

ALTER TABLE custom_product_items ALTER COLUMN status_updated_at SET NOT NULL;
