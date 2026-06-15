package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.service.salesbot.SalesbotTrigger;

/**
 * Адаптер {@link SalesbotTrigger} поверх {@link AmoCrmGateway} (запрос №2, fire-and-forget).
 */
@Component
@AllArgsConstructor
class SalesbotTriggerAmoAdapter implements SalesbotTrigger {

    private final AmoCrmGateway amoCrmGateway;

    @Override
    public boolean run(Long leadId, Long botId) {
        return amoCrmGateway.runSalesbot(leadId, botId);
    }
}
