package ru.anyforms.service.salesbot;

import ru.anyforms.model.salesbot.BotRunType;

/**
 * Запись результата попытки в лог (write-side, ISP).
 * <p>
 * Реализация ОБЯЗАНА быть идемпотентной по ключу {@code (lead_id, bot_id)}
 * (upsert / ON CONFLICT) — это жёсткий бэкстоп против двойной отправки.
 */
public interface BotExecutionRecorder {

    /** Успешный запуск шага дрип-цепочки группы (запрос ушёл в amoCRM). */
    void recordGroupSuccess(Long leadId, Long groupId, BotStep step);

    /** Неуспех шага цепочки: лид вышел из статуса или запрос упал. Бот НЕ считается отправленным. */
    void recordGroupFailed(Long leadId, Long groupId, BotStep step);

    /** Успешный служебный запуск (ручной, доставка, повторные продажи). */
    void recordSuccess(Long leadId, BotRunType type, BotStep step);

    /** Неуспешный служебный запуск. */
    void recordFailed(Long leadId, BotRunType type, BotStep step);
}
