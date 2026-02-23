package ru.anyforms.service.amo.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.amo.AmoNewMessageWebhookPayload;
import ru.anyforms.service.amo.AmoNewMessageProcessor;

@Log4j2
@Service
class AmoNewMessageProcessorImpl implements AmoNewMessageProcessor {

    @Override
    public void process(AmoNewMessageWebhookPayload payload) {

    }
}
