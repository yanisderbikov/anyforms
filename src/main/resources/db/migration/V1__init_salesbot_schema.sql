-- ============================================================================
--  V1 — исходная схема salesbot, как описано в ТЗ (до наших доработок).
--  Все времена — в UTC. weekday — ISO-8601: 1 = понедельник ... 7 = воскресенье.
-- ============================================================================

-- Маппинг тип заказа -> воронка/статус amoCRM.
CREATE TABLE order_type_funnel (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type        VARCHAR(32) NOT NULL,
    pipeline_id BIGINT      NOT NULL,
    status_id   BIGINT      NOT NULL,
    CONSTRAINT uq_order_type_funnel_type UNIQUE (type)
);

-- Порядок ботов для каждого типа.
CREATE TABLE bot_sequence (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bot_id   BIGINT      NOT NULL,
    position INTEGER     NOT NULL,
    type     VARCHAR(32) NOT NULL,
    CONSTRAINT uq_bot_sequence_type_position UNIQUE (type, position)
);

-- Журнал отработавших ботов (источник истины о прогрессе).
-- UNIQUE(lead_id, bot_id) — жёсткий бэкстоп против двойной отправки.
CREATE TABLE bot_execution_log (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lead_id       BIGINT      NOT NULL,
    bot_id        BIGINT      NOT NULL,
    position      INTEGER     NOT NULL,
    type          VARCHAR(32) NOT NULL,
    status        VARCHAR(16) NOT NULL,            -- SUCCESS | FAILED
    date_executed TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_bot_execution_log_lead_bot UNIQUE (lead_id, bot_id)
);

-- Глобальное расписание слотов (одно на все типы).
CREATE TABLE schedule (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    weekday  INTEGER NOT NULL,           -- ISO: 1=Пн ... 7=Вс
    time_utc TIME    NOT NULL,           -- базовое (некруглое) время суток в UTC
    enabled  BOOLEAN NOT NULL DEFAULT TRUE
);
