package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.service.salesbot.FunnelTarget;
import ru.anyforms.service.salesbot.LeadStatusVerifier;

import java.util.Objects;

/**
 * Адаптер {@link LeadStatusVerifier} поверх {@link AmoCrmGateway}: перечитывает сделку
 * и сверяет её текущую воронку/статус с целевыми.
 */
@Slf4j
@Component
@AllArgsConstructor
class LeadStatusVerifierAmoAdapter implements LeadStatusVerifier {

    private final AmoCrmGateway amoCrmGateway;

    @Override
    public boolean isInTargetStatus(Long leadId, FunnelTarget target) {
        try {
            AmoLead lead = amoCrmGateway.getLead(leadId);
            if (lead == null) {
                return false;
            }
            return Objects.equals(lead.getPipelineId(), target.pipelineId())
                    && Objects.equals(lead.getStatusId(), target.statusId());
        } catch (Exception e) {
            // Не удалось прочитать статус — безопаснее не слать бота.
            log.warn("Failed to verify status for lead {}: {}", leadId, e.getMessage());
            return false;
        }
    }
}
