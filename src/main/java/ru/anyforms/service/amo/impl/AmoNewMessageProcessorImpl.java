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
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    private static Set<Long> skippingContactIds = Set.of(
            68033269L, // молька
            69185457L, // чел бразилия
            68050553L // дима
    );

    @Override
    public void process(AmoNewMessageWebhookPayload payload) {
        log.info("\nСообщение: {}\nканал: {}",payload.getMessage().getText(), payload.getMessage().getOrigin());
        var contactId = payload.getMessage().getContactId();
        if (skippingContactIds.contains(contactId)) {
            return;
        }
        var lead  = amoCrmGateway.getLead(payload.getMessage().getEntity().getId());
        var pipelineId = lead.getPipelineId();
        if (!pipelineId.equals(AmoPipeline.TRASH.getPipelineId())) {
            if (lead.getResponsibleUserId().equals(AmoTaskResponsibleUser.IAN.getResponsibleUserId())) {
                if (leadIdTaskCache.getIfPresent(lead.getId()) == null) {
                    amoCrmGateway.setNewTask(
                            AmoTaskResponsibleUser.IAN.getResponsibleUserId(),
                            AmoTaskId.LOST_MESSAGE.getTaskId(),
                            "Похоже что нужно ответить",
                            lead.getId(),
                            10
                    );
                    leadIdTaskCache.put(lead.getId(), Boolean.TRUE);
                }
            }
            return;
        }
        var message = payload.getMessage().getText();
        if (MessagePatternOrder.isNeedToMove(message)) {
            amoCrmGateway.updateLeadStatus(lead.getId(), AmoLeadStatus.FIST_TOUCH);
            if (leadIdTaskCache.getIfPresent(lead.getId()) == null) {
                amoCrmGateway.setNewTask(
                        AmoTaskResponsibleUser.IAN.getResponsibleUserId(),
                        AmoTaskId.LOST_MESSAGE.getTaskId(),
                        "Проверка",
                        lead.getId(),
                        10
                );
                leadIdTaskCache.put(lead.getId(), Boolean.TRUE);
            }
        }
    }
}
