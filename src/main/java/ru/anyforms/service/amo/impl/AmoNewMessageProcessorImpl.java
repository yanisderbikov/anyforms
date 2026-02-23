package ru.anyforms.service.amo.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.amo.AmoNewMessageWebhookPayload;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoPipeline;
import ru.anyforms.service.amo.AmoNewMessageProcessor;

import java.util.Set;

@Slf4j
@Service
class AmoNewMessageProcessorImpl implements AmoNewMessageProcessor {

    private AmoCrmGateway amoCrmGateway;
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
            return;
        }


    }
}
