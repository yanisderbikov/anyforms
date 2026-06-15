package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.repository.BotSequenceRepository;
import ru.anyforms.service.salesbot.BotSequenceProvider;
import ru.anyforms.service.salesbot.BotStep;

import java.util.List;

/**
 * Адаптер {@link BotSequenceProvider} поверх {@link BotSequenceRepository}.
 */
@Component
@AllArgsConstructor
class BotSequenceProviderImpl implements BotSequenceProvider {

    private final BotSequenceRepository repository;

    @Override
    public List<BotStep> sequenceFor(OrderType type) {
        return repository.findByTypeOrderByPositionAsc(type).stream()
                .map(b -> new BotStep(b.getBotId(), b.getPosition()))
                .toList();
    }
}
