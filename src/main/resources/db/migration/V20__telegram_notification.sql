CREATE TABLE telegram_notification (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_telegram_notification_order UNIQUE (order_id)
);

-- Телеграм-уведомления больше не ходят через task: тип RETAIL_ORDER_TELEGRAM упразднён,
-- его строки (включая зависшие NEW/FAILED после сетевых ошибок) вычищаются.
DELETE FROM task WHERE type = 'RETAIL_ORDER_TELEGRAM';
