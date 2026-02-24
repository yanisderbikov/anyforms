package ru.anyforms.service.amo.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.amo.AmoNewMessageWebhookPayload;
import ru.anyforms.service.amo.AmoNewMessageWebhookParser;
import ru.anyforms.util.AmoNewMessagePayloadParser;
import ru.anyforms.util.FormDataParser;

import java.util.Map;

@Service
public class AmoNewMessageWebhookParserImpl implements AmoNewMessageWebhookParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AmoNewMessageWebhookPayload parse(String contentType, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            return parseJson(body);
        }
        return parseFormUrlEncoded(body);
    }

    private AmoNewMessageWebhookPayload parseJson(String body) {
        try {
            return objectMapper.readValue(body, AmoNewMessageWebhookPayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    private AmoNewMessageWebhookPayload parseFormUrlEncoded(String body) {
        Map<String, Object> parsed = FormDataParser.parse(body);
        return AmoNewMessagePayloadParser.parse(parsed);
    }
}
