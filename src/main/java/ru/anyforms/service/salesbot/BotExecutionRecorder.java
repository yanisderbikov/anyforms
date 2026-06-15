package ru.anyforms.service.salesbot;

import ru.anyforms.model.salesbot.OrderType;

/**
 * Запись результата попытки в лог (write-side, ISP).
 * <p>
 * Реализация ОБЯЗАНА быть идемпотентной по ключу {@code (lead_id, bot_id)}
 * (upsert / ON CONFLICT) — это жёсткий бэкстоп против двойной отправки.
 */
public interface BotExecutionRecorder {

    /** Фиксирует успешный запуск бота (запрос ушёл в amoCRM). */
    void recordSuccess(Long leadId, OrderType type, BotStep step);

    /**
     * Фиксирует неуспех: лид уже не в целевом статусе на момент запуска,
     * либо запрос на запуск завершился ошибкой. Бот при этом НЕ считается отправленным.
     */
    void recordFailed(Long leadId, OrderType type, BotStep step);
}
