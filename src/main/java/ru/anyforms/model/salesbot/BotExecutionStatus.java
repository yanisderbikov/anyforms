package ru.anyforms.model.salesbot;

/**
 * Результат попытки запуска бота для лида, фиксируется в {@code bot_execution_log}.
 * <p>
 * Прогресс цепочки определяется ТОЛЬКО по {@link #SUCCESS}: «следующий бот» — это
 * первая позиция из {@code bot_sequence}, для которой у лида ещё нет записи
 * со статусом {@code SUCCESS}.
 */
public enum BotExecutionStatus {
    /** Запрос на запуск бота ушёл в amoCRM успешно (fire-and-forget). */
    SUCCESS,
    /** Бот не запущен: лид уже не в целевом статусе на момент запуска, либо ошибка запроса. */
    FAILED
}
