package ru.anyforms.service.salesbot.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoLeadStatus;
import ru.anyforms.service.salesbot.MessageDeliveryFailureHandler;

/**
 * Переносит лид в статус «нереализовано» (воронка/статус берутся из конфига
 * {@code salesbot.fail.*}) через {@link AmoCrmGateway#updateLeadStatus(Long, Long, Long)}.
 */
@Slf4j
@Component
class MessageDeliveryFailureHandlerImpl implements MessageDeliveryFailureHandler {

    private final AmoCrmGateway amoCrmGateway;
    private final Long failStatusId;

    MessageDeliveryFailureHandlerImpl(AmoCrmGateway amoCrmGateway) {
        this.amoCrmGateway = amoCrmGateway;
        this.failStatusId = 143L;
    }

    @Override
    public boolean onSendFailed(Long leadId) {
        if (leadId == null) {
            log.warn("fail-send-message: leadId is null, nothing to move");
            return false;
        }
        boolean ok = amoCrmGateway.updateLeadStatus(leadId,  AmoLeadStatus.NOT_REALIZED);
        if (ok) {
            log.info("Lead {} moved to 'нереализовано' (status={}) after message send failure",
                    leadId, failStatusId);
        } else {
            log.error("Failed to move lead {} to 'нереализовано' (status={})",
                    leadId, failStatusId);
        }
        return ok;
    }
}
