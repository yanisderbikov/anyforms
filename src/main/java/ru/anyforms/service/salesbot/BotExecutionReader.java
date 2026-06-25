package ru.anyforms.service.salesbot;

import ru.anyforms.model.salesbot.OrderType;

import java.time.Instant;
import java.util.Set;

/**
 * Чтение прогресса цепочки из лога (read-side, ISP).
 * Отделён от {@link BotExecutionRecorder}, т.к. {@link NextBotResolver} только читает.
 */
public interface BotExecutionReader {

    /**
     * Позиции, которые для данного лида и типа уже отработали успешно
     * ({@code status = SUCCESS}). Прогресс считается только по success.
     *
     * @param leadId ID сделки
     * @param type   тип заказа (позиции уникальны в рамках типа)
     * @return множество успешно отработавших позиций (может быть пустым)
     */
    Set<Integer> successPositions(Long leadId, OrderType type);

    /**
     * Был ли лиду СЕГОДНЯ (в UTC-сутки, которым принадлежит {@code now}) уже успешно
     * отправлен бот. Дневной guard: гарантирует, что лид не «исполнится» дважды за сутки
     * (например, при рестарте/catch-up прогоне). Проверяется только по нашей БД, без amo.
     */
    boolean alreadySentToday(Long leadId, Instant now);

    /**
     * Был ли указанный бот уже успешно запущен для указанного лида.
     * Дедуп ручного массового запуска (вне дрип-цепочки): бот не уходит лиду дважды.
     */
    boolean alreadyExecuted(Long leadId, Long botId);
}
