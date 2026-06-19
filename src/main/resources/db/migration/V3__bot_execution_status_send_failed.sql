-- ============================================================================
--  V3 — расширяем bot_execution_log.status под новое значение MESSAGE_SEND_FAILED
--  (19 символов) — старое объявление VARCHAR(16) его не вмещает.
--  Нативного PG-enum / CHECK на status нет, поэтому достаточно увеличить длину.
--  Идемпотентно: ALTER TYPE до большей длины безопасно повторять.
-- ============================================================================

ALTER TABLE bot_execution_log
    ALTER COLUMN status TYPE VARCHAR(32);

COMMENT ON COLUMN bot_execution_log.status IS 'SUCCESS | FAILED | MESSAGE_SEND_FAILED';
