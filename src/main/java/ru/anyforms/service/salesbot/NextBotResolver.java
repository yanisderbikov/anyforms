package ru.anyforms.service.salesbot;

import ru.anyforms.model.salesbot.OrderType;

import java.util.Optional;

/**
 * Доменный сервис выбора следующего бота для лида.
 * <p>
 * «Следующий бот» = первая позиция из {@link BotSequenceProvider#sequenceFor(OrderType)},
 * для которой у лида ещё НЕТ записи в логе со статусом success
 * (см. {@link BotExecutionReader#successPositions}).
 */
public interface NextBotResolver {

    /**
     * @return следующий бот к запуску, либо {@link Optional#empty()}, если вся цепочка
     *         уже отработала (все позиции success) — тогда лиду больше ничего не шлём.
     */
    Optional<BotStep> nextBot(OrderType type, Long leadId);
}
