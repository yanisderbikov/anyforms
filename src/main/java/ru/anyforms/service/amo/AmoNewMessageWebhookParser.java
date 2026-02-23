package ru.anyforms.service.amo;

import ru.anyforms.dto.amo.AmoNewMessageWebhookPayload;
import ru.anyforms.util.AmoNewMessagePayloadParser;
import ru.anyforms.util.FormDataParser;

import java.util.Map;

/**
 * Парсит тело вебхука new-message в AmoNewMessageWebhookPayload.
 * Поддерживает application/json и application/x-www-form-urlencoded.
 */
public interface AmoNewMessageWebhookParser {
    AmoNewMessageWebhookPayload parse(String contentType, String body);
}
