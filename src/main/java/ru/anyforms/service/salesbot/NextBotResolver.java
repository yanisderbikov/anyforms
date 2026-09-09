package ru.anyforms.service.salesbot;

import java.util.Optional;

/**
 * Доменный сервис выбора следующего бота для лида в группе.
 * <p>
 * «Следующий бот» = первая позиция из {@link BotSequenceProvider#sequenceFor(Long)},
 * для которой у лида ещё НЕТ записи в логе со статусом success
 * (см. {@link BotExecutionReader#successPositions}).
 */
public interface NextBotResolver {

    /**
     * @return следующий бот к запуску, либо {@link Optional#empty()}, если вся цепочка
     *         уже отработала (все позиции success) — тогда лиду больше ничего не шлём.
     */
    Optional<BotStep> nextBot(Long groupId, Long leadId);
}
