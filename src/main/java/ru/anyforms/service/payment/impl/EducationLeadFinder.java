package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.amo.AmoLeadStatus;

import java.util.List;

@Component
@RequiredArgsConstructor
class EducationLeadFinder {

    private final AmoCrmGateway amoCrmGateway;

    @Value("${amocrm.education.pipeline.id}")
    private Long educationPipelineId;

    Long findLatestActiveLead(Long contactId) {
        List<Long> leadIds = amoCrmGateway.getLeadIdsByContact(contactId);
        Long latestActive = null;
        for (Long leadId : leadIds) {
            AmoLead lead = amoCrmGateway.getLead(leadId);
            if (lead == null
                    || !educationPipelineId.equals(lead.getPipelineId())
                    || isClosed(lead.getStatusId())) {
                continue;
            }
            if (latestActive == null || leadId > latestActive) {
                latestActive = leadId;
            }
        }
        return latestActive;
    }

    private static boolean isClosed(Long statusId) {
        return AmoLeadStatus.REALIZED.getStatusId().equals(statusId)
                || AmoLeadStatus.NOT_REALIZED.getStatusId().equals(statusId);
    }
}
