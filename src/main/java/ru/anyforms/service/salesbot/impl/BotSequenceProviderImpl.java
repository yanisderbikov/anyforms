package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.repository.BotSequenceRepository;
import ru.anyforms.service.salesbot.BotSequenceProvider;
import ru.anyforms.service.salesbot.BotStep;

import java.util.List;

/** Адаптер {@link BotSequenceProvider} поверх {@link BotSequenceRepository}. */
@Component
@AllArgsConstructor
class BotSequenceProviderImpl implements BotSequenceProvider {

    private final BotSequenceRepository repository;

    @Override
    public List<BotStep> sequenceFor(Long groupId) {
        return repository.findByGroupIdOrderByPositionAsc(groupId).stream()
                .map(b -> new BotStep(b.getBotId(), b.getPosition(), b.getDelayMinutes()))
                .toList();
    }
}
