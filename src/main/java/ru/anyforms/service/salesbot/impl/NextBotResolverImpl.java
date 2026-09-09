package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.service.salesbot.BotExecutionReader;
import ru.anyforms.service.salesbot.BotSequenceProvider;
import ru.anyforms.service.salesbot.BotStep;
import ru.anyforms.service.salesbot.NextBotResolver;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Чистая доменная логика выбора следующего бота: первая позиция из цепочки группы,
 * для которой у лида ещё нет success-записи в логе.
 */
@Component
@AllArgsConstructor
class NextBotResolverImpl implements NextBotResolver {

    private final BotSequenceProvider sequenceProvider;
    private final BotExecutionReader executionReader;

    @Override
    public Optional<BotStep> nextBot(Long groupId, Long leadId) {
        List<BotStep> sequence = sequenceProvider.sequenceFor(groupId);
        if (sequence.isEmpty()) {
            return Optional.empty();
        }
        Set<Integer> done = executionReader.successPositions(leadId, groupId);
        return sequence.stream()
                .filter(step -> !done.contains(step.position()))
                .findFirst();
    }
}
