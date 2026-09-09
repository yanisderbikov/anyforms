-- ============================================================================
--  V53 — интервалы между шагами цепочки и регулярный прогон вместо слотов расписания.
--  У каждого шага своя задержка (минуты): для шага 1 — от момента, когда прогон впервые
--  увидел сделку в статусе группы (bot_group_lead_seen), для остальных — от успешной
--  отправки предыдущего шага (bot_execution_log). Таблица schedule больше не нужна:
--  прогон идёт каждые N минут в рабочем окне (salesbot.run.*).
-- ============================================================================

ALTER TABLE bot_sequence ADD COLUMN delay_minutes INTEGER NOT NULL DEFAULT 1440;
COMMENT ON COLUMN bot_sequence.delay_minutes IS 'Не раньше чем через N минут после предыдущего шага (для шага 1 — после попадания сделки в статус группы)';

CREATE TABLE bot_group_lead_seen (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id      BIGINT      NOT NULL REFERENCES bot_group (id) ON DELETE CASCADE,
    lead_id       BIGINT      NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_bot_group_lead_seen UNIQUE (group_id, lead_id)
);

DROP TABLE IF EXISTS schedule;
