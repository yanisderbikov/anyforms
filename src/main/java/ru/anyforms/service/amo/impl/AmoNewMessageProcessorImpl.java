package ru.anyforms.service.amo.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.amo.AmoNewMessageWebhookPayload;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoLeadStatus;
import ru.anyforms.model.amo.AmoPipeline;
import ru.anyforms.model.amo.AmoTaskId;
import ru.anyforms.model.amo.AmoTaskResponsibleUser;
import ru.anyforms.service.amo.AmoNewMessageProcessor;
import ru.anyforms.util.pattern.MessagePatternOrder;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
class AmoNewMessageProcessorImpl implements AmoNewMessageProcessor {

    private final AmoCrmGateway amoCrmGateway;

    /**
     * Кеш leadId, для которых уже вызывался setNewTask. TTL 1 час — повторно задачу не ставим.
     */
    private final Cache<Long, Boolean> leadIdTaskCache = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build();

    private static Set<Long> skippingContactIds = Set.of(
            68033269L, // молька
            69185457L, // чел бразилия
            68050553L // дима
    );

    private static final String SYSTEM_WZ_MARKER = "=== SYSTEM WZ ===";

    @Override
    public void process(AmoNewMessageWebhookPayload payload) {
        var contactId = payload.getMessage().getContactId();
        if (skippingContactIds.contains(contactId)) {
            return;
        }
        var message = payload.getMessage().getText();
        if (message != null && message.contains(SYSTEM_WZ_MARKER)) {
            return;
        }
        var lead  = amoCrmGateway.getLead(payload.getMessage().getEntity().getId());
        var pipelineId = lead.getPipelineId();
        if (!pipelineId.equals(AmoPipeline.TRASH.getPipelineId())) {
            if (!lead.getStatusId().equals(AmoLeadStatus.FIST_TOUCH.getStatusId()) && lead.getResponsibleUserId().equals(AmoTaskResponsibleUser.IAN.getResponsibleUserId())) {
                setTaskIfAbsent(lead.getId(), "Похоже что нужно ответить");
            }
            return;
        }
        if (MessagePatternOrder.isNeedToMove(message)) {
            amoCrmGateway.updateLeadStatus(lead.getId(), AmoLeadStatus.FIST_TOUCH);
            setTaskIfAbsent(lead.getId(), "Проверка");
        } else {
            log.info("не передвинули сообщение сделки {} {}", lead.getId(), message);
        }
    }

    private void setTaskIfAbsent(Long leadId, String text) {
        if (leadIdTaskCache.getIfPresent(leadId) != null) {
            return;
        }
        if (amoCrmGateway.hasIncompleteTask(leadId)) {
            leadIdTaskCache.put(leadId, Boolean.TRUE);
            return;
        }
        amoCrmGateway.setNewTask(
                AmoTaskResponsibleUser.IAN.getResponsibleUserId(),
                AmoTaskId.LOST_MESSAGE.getTaskId(),
                text,
                leadId,
                10
        );
        leadIdTaskCache.put(leadId, Boolean.TRUE);
    }
}
