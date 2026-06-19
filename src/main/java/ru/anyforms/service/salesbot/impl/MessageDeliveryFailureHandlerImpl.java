package ru.anyforms.service.salesbot.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoLeadStatus;
import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.repository.BotExecutionLogRepository;
import ru.anyforms.service.salesbot.MessageDeliveryFailureHandler;

/**
 * Переносит лид в статус «нереализовано» (воронка/статус берутся из конфига
 * {@code salesbot.fail.*}) через {@link AmoCrmGateway#updateLeadStatus(Long, Long, Long)}
 * и помечает последнюю запись лида в {@code bot_execution_log} статусом
 * {@link BotExecutionStatus#MESSAGE_SEND_FAILED}.
 */
@Slf4j
@Component
class MessageDeliveryFailureHandlerImpl implements MessageDeliveryFailureHandler {

    private final AmoCrmGateway amoCrmGateway;
    private final BotExecutionLogRepository botExecutionLogRepository;
    private final Long failStatusId;

    MessageDeliveryFailureHandlerImpl(AmoCrmGateway amoCrmGateway,
                                      BotExecutionLogRepository botExecutionLogRepository) {
        this.amoCrmGateway = amoCrmGateway;
        this.botExecutionLogRepository = botExecutionLogRepository;
        this.failStatusId = 143L;
    }

    @Override
    @Transactional
    public boolean onSendFailed(Long leadId) {
        if (leadId == null) {
            log.warn("fail-send-message: leadId is null, nothing to move");
            return false;
        }

        int updated = botExecutionLogRepository.markLatestStatus(
                leadId, BotExecutionStatus.MESSAGE_SEND_FAILED.name());
        if (updated > 0) {
            log.info("Lead {}: latest bot_execution_log marked as {}", leadId, BotExecutionStatus.MESSAGE_SEND_FAILED);
        } else {
            log.warn("Lead {}: no bot_execution_log row to mark as {}", leadId, BotExecutionStatus.MESSAGE_SEND_FAILED);
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
