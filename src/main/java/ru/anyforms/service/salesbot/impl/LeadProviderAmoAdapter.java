package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.service.salesbot.FunnelTarget;
import ru.anyforms.service.salesbot.LeadProvider;

import java.util.List;

/**
 * Адаптер {@link LeadProvider} поверх {@link AmoCrmGateway} (запрос №1).
 * Развязывает оркестратор от конкретного amo-клиента (DIP).
 */
@Component
@AllArgsConstructor
class LeadProviderAmoAdapter implements LeadProvider {

    private final AmoCrmGateway amoCrmGateway;

    @Override
    public List<Long> leadsInStatus(FunnelTarget target) {
        return amoCrmGateway.getLeadIdsByStatus(target.pipelineId(), target.statusId());
    }
}
