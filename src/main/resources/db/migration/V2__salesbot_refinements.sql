-- ============================================================================
--  V2 — самодостаточная и идемпотентная.
--  ВАЖНО: на уже существующих (непустых) БД V1 могла быть «забейслайнена»
--  (baseline-on-migrate) и фактически НЕ создать таблицы. Поэтому здесь не полагаемся
--  на результат V1: гарантируем наличие всех salesbot-таблиц и приводим
--  schedule.time_utc к timestamptz (поле маппится на java.time.Instant) независимо от
--  того, отработала V1 или нет. Дата в time_utc — плейсхолдер 1970-01-01, значимо
--  только UTC-время суток (на рантайме добавляется джиттер).
-- ============================================================================

-- (0) Гарантируем наличие таблиц (на свежей БД их уже создала V1 → no-op).
CREATE TABLE IF NOT EXISTS order_type_funnel (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type        VARCHAR(32) NOT NULL,
    pipeline_id BIGINT      NOT NULL,
    status_id   BIGINT      NOT NULL,
    CONSTRAINT uq_order_type_funnel_type UNIQUE (type)
);

CREATE TABLE IF NOT EXISTS bot_sequence (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bot_id   BIGINT      NOT NULL,
    position INTEGER     NOT NULL,
    type     VARCHAR(32) NOT NULL,
    CONSTRAINT uq_bot_sequence_type_position UNIQUE (type, position)
);

CREATE TABLE IF NOT EXISTS bot_execution_log (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lead_id       BIGINT      NOT NULL,
    bot_id        BIGINT      NOT NULL,
    position      INTEGER     NOT NULL,
    type          VARCHAR(32) NOT NULL,
    status        VARCHAR(16) NOT NULL,            -- SUCCESS | FAILED
    date_executed TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_bot_execution_log_lead_bot UNIQUE (lead_id, bot_id)
);

CREATE TABLE IF NOT EXISTS schedule (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    weekday  INTEGER     NOT NULL,           -- ISO: 1=Пн ... 7=Вс
    time_utc TIMESTAMPTZ NOT NULL,           -- сразу финальный тип
    enabled  BOOLEAN     NOT NULL DEFAULT TRUE
);

-- (1) Если schedule досталась от V1 со столбцом TIME — конвертируем в timestamptz.
--     (Если таблица только что создана как timestamptz — условие ложно, ALTER пропущен.)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'schedule'
          AND column_name = 'time_utc'
          AND data_type = 'time without time zone'
    ) THEN
        ALTER TABLE schedule
            ALTER COLUMN time_utc TYPE TIMESTAMPTZ
            USING ((DATE '1970-01-01' + time_utc) AT TIME ZONE 'UTC');
    END IF;
END $$;

-- (2) Сид расписания — только если таблица пуста (не затираем ручные правки).
--     Ориентир (МСК) -> UTC = МСК-3: Пн 12:10 МСК -> 09:10 UTC; Ср ~17:06 -> 14:06;
--     Чт ~15:23 -> 12:23; Пт ~19:47 -> 16:47; Вс ~12:14 -> 09:14.
INSERT INTO schedule (weekday, time_utc, enabled)
SELECT v.weekday, v.time_utc, v.enabled
FROM (VALUES
    (1, TIMESTAMPTZ '1970-01-01 09:10:00+00', TRUE),
    (3, TIMESTAMPTZ '1970-01-01 14:06:00+00', TRUE),
    (4, TIMESTAMPTZ '1970-01-01 12:23:00+00', TRUE),
    (5, TIMESTAMPTZ '1970-01-01 16:47:00+00', TRUE),
    (7, TIMESTAMPTZ '1970-01-01 09:14:00+00', TRUE)
) AS v(weekday, time_utc, enabled)
WHERE NOT EXISTS (SELECT 1 FROM schedule);

-- ============================================================================
--  ПРИМЕР сидов воронок и цепочек (ЗАПОЛНИТЬ РЕАЛЬНЫМИ ID; bot_id/pipeline/status — плейсхолдеры).
-- ============================================================================
-- INSERT INTO order_type_funnel (type, pipeline_id, status_id) VALUES
--     ('RETAIL', 10557858, 86451842);
-- INSERT INTO bot_sequence (type, position, bot_id) VALUES
--     ('RETAIL', 1, 23489), ('RETAIL', 2, 23491), ('RETAIL', 3, 23493);
