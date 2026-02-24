package ru.anyforms.service.amo;

import ru.anyforms.dto.amo.AmoNewMessageWebhookPayload;

public interface AmoNewMessageProcessor {
    void process(AmoNewMessageWebhookPayload payload);
}
