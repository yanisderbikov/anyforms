-- ============================================================================
--  V54 — окно отправки у группы: в какое время суток (Москва) могут уходить боты цепочки.
--  Хранится как timestamptz (маппится на java.time.Instant), как раньше слоты schedule:
--  дата — плейсхолдер 1970-01-01, значимо только время суток (по Москве, см. BotGroup).
--  Оба поля NULL — используется окно по умолчанию (salesbot.run.window-msk). Если задержка
--  шага истекает вне окна, бот уходит при первой проверке в следующее открытие окна.
-- ============================================================================

ALTER TABLE bot_group
    ADD COLUMN send_from TIMESTAMPTZ,
    ADD COLUMN send_to   TIMESTAMPTZ,
    ADD CONSTRAINT chk_bot_group_send_window CHECK (
        (send_from IS NULL AND send_to IS NULL) OR (send_from IS NOT NULL AND send_to IS NOT NULL AND send_from < send_to)
    );
COMMENT ON COLUMN bot_group.send_from IS 'Начало окна отправки: timestamptz с датой 1970-01-01, значимо время суток по Москве; NULL — окно по умолчанию';
COMMENT ON COLUMN bot_group.send_to   IS 'Конец окна отправки (исключительно), тот же формат';
