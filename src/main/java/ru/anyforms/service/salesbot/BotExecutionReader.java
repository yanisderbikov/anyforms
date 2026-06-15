package ru.anyforms.service.salesbot;

import ru.anyforms.model.salesbot.OrderType;

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
}
