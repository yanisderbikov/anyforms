package ru.anyforms.service.salesbot;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Чтение прогресса цепочки из лога (read-side, ISP).
 * Отделён от {@link BotExecutionRecorder}, т.к. {@link NextBotResolver} только читает.
 */
public interface BotExecutionReader {

    /**
     * Позиции, которые для данного лида в данной группе уже отработали успешно
     * ({@code status = SUCCESS}). Прогресс считается только по success.
     */
    Set<Integer> successPositions(Long leadId, Long groupId);

    /** Момент последней успешной отправки лиду в группе — якорь задержки следующего шага. */
    Optional<Instant> lastSuccessAt(Long leadId, Long groupId);

    /** Был ли указанный бот уже успешно запущен для указанного лида (любой группой/типом). */
    boolean alreadyExecuted(Long leadId, Long botId);
}
