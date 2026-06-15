package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.service.salesbot.BotExecutionReader;
import ru.anyforms.service.salesbot.BotSequenceProvider;
import ru.anyforms.service.salesbot.BotStep;
import ru.anyforms.service.salesbot.NextBotResolver;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Чистая доменная логика выбора следующего бота: первая позиция из цепочки типа,
 * для которой у лида ещё нет success-записи в логе.
 * <p>
 * Если в {@link BotSequenceProvider} позже добавят новую позицию (была 9 — стала 10),
 * она автоматически окажется «следующей» для всех, у кого 1–9 уже success.
 */
@Component
@AllArgsConstructor
class NextBotResolverImpl implements NextBotResolver {

    private final BotSequenceProvider sequenceProvider;
    private final BotExecutionReader executionReader;

    @Override
    public Optional<BotStep> nextBot(OrderType type, Long leadId) {
        List<BotStep> sequence = sequenceProvider.sequenceFor(type);
        if (sequence.isEmpty()) {
            return Optional.empty();
        }
        Set<Integer> done = executionReader.successPositions(leadId, type);
        return sequence.stream()
                .filter(step -> !done.contains(step.position()))
                .findFirst();
    }
}
