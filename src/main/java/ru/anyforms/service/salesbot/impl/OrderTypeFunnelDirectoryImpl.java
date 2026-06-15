package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.repository.OrderTypeFunnelRepository;
import ru.anyforms.service.salesbot.FunnelTarget;
import ru.anyforms.service.salesbot.OrderTypeFunnelDirectory;

import java.util.List;
import java.util.Optional;

/**
 * Адаптер {@link OrderTypeFunnelDirectory} поверх {@link OrderTypeFunnelRepository}.
 */
@Slf4j
@Component
@AllArgsConstructor
class OrderTypeFunnelDirectoryImpl implements OrderTypeFunnelDirectory {

    private final OrderTypeFunnelRepository repository;

    @Override
    public List<OrderType> configuredTypes() {
        return repository.findAll().stream()
                .map(ru.anyforms.model.salesbot.OrderTypeFunnel::getType)
                .distinct()
                .toList();
    }

    @Override
    public Optional<FunnelTarget> targetFor(OrderType type) {
        return repository.findByType(type)
                .map(f -> new FunnelTarget(f.getPipelineId(), f.getStatusId()));
    }
}
