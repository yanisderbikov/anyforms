package ru.anyforms.service.amo.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.amo.AmoNewMessageWebhookPayload;
import ru.anyforms.service.amo.AmoNewMessageProcessor;

@Slf4j
@Service
class AmoNewMessageProcessorImpl implements AmoNewMessageProcessor {

    @Override
    public void process(AmoNewMessageWebhookPayload payload) {
        log.info("\nСообщение: {}\nканал: {}",payload.getMessage().getText(), payload.getMessage().getOrigin());
    }
}
