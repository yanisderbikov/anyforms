-- ============================================================================
--  V52 — дрип-цепочки переезжают с фиксированных типов заказа (RETAIL, CUSTOM, …)
--  на группы с произвольным названием. Группа = название + воронка/статус amoCRM +
--  цепочка ботов. Существующие воронки и цепочки переносятся в группы, журнал
--  получает ссылку на группу; тип записи журнала для цепочек становится 'DRIP'.
-- ============================================================================

CREATE TABLE bot_group (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    pipeline_id BIGINT,                       -- NULL: воронка ещё не задана, группа не обрабатывается
    status_id   BIGINT,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    legacy_type VARCHAR(32)                   -- временно: из какого типа заказа перенесено
);

-- Группа на каждый дрип-тип, который где-либо встречается (воронка, цепочка или журнал).
INSERT INTO bot_group (name, pipeline_id, status_id, legacy_type)
SELECT CASE t.type
           WHEN 'RETAIL'        THEN 'Розница'
           WHEN 'RETAIL_REPEAT' THEN 'Розница, повтор'
           WHEN 'CUSTOM'        THEN 'Под заказ'
           WHEN 'CUSTOM_REPEAT' THEN 'Под заказ, повтор'
           ELSE t.type
       END,
       f.pipeline_id,
       f.status_id,
       t.type
FROM (
    SELECT type FROM order_type_funnel
    UNION SELECT type FROM bot_sequence
    UNION SELECT type FROM bot_execution_log
) t
LEFT JOIN order_type_funnel f ON f.type = t.type
WHERE t.type IN ('RETAIL', 'RETAIL_REPEAT', 'CUSTOM', 'CUSTOM_REPEAT')
ORDER BY t.type;

-- bot_sequence: type -> group_id.
ALTER TABLE bot_sequence ADD COLUMN group_id BIGINT;
UPDATE bot_sequence s SET group_id = g.id FROM bot_group g WHERE g.legacy_type = s.type;
DELETE FROM bot_sequence WHERE group_id IS NULL;
ALTER TABLE bot_sequence
    ALTER COLUMN group_id SET NOT NULL,
    DROP CONSTRAINT uq_bot_sequence_type_position,
    DROP COLUMN type,
    ADD CONSTRAINT fk_bot_sequence_group FOREIGN KEY (group_id) REFERENCES bot_group (id) ON DELETE CASCADE,
    ADD CONSTRAINT uq_bot_sequence_group_position UNIQUE (group_id, position);

-- bot_execution_log: записи цепочек получают group_id и тип DRIP; служебные типы не трогаем.
ALTER TABLE bot_execution_log
    ADD COLUMN group_id BIGINT REFERENCES bot_group (id) ON DELETE SET NULL;
UPDATE bot_execution_log l SET group_id = g.id, type = 'DRIP' FROM bot_group g WHERE g.legacy_type = l.type;
CREATE INDEX ix_bot_execution_log_group_lead ON bot_execution_log (group_id, lead_id);
COMMENT ON COLUMN bot_execution_log.type IS 'DRIP (цепочка группы) | MANUAL | DELIVERY | RETAIL_TO_REPEAT';

ALTER TABLE bot_group DROP COLUMN legacy_type;
DROP TABLE order_type_funnel;
