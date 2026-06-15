package ru.anyforms.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.service.salesbot.MessageDeliveryFailureHandler;

import java.util.List;

/**
 * Вебхуки SalesBot-дрипа от amoCRM.
 * <p>
 * {@code POST /webhook/amocrm/fail-send-message} — amoCRM сообщает, что бот не смог
 * отправить сообщение лиду; переносим лид в «нереализовано».
 * <p>
 * lead_id извлекается гибко: из form/query-параметра ({@code lead_id}, {@code leadId},
 * {@code entity_id}, {@code element_id}) либо из JSON-тела с теми же ключами. Настрой в
 * SalesBot отправку lead_id любым из этих способов (проще всего form-поле {@code lead_id={{lead.id}}}).
 */
@RestController
@RequestMapping("/webhook/amocrm")
@Slf4j
public class SalesbotWebhookController {

    private static final List<String> LEAD_ID_KEYS = List.of("lead_id", "leadId", "entity_id", "element_id");

    private final MessageDeliveryFailureHandler failureHandler;

    public SalesbotWebhookController(MessageDeliveryFailureHandler failureHandler) {
        this.failureHandler = failureHandler;
    }

    @PostMapping(value = "/fail-send-message",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> failSendMessage(
            @RequestParam(required = false) MultiValueMap<String, String> formData,
            @RequestBody(required = false) String body) {
        try {
            Long leadId = extractLeadId(formData, body);
            if (leadId == null) {
                log.warn("fail-send-message: lead_id не найден. form={}, body={}", formData, body);
                return ResponseEntity.badRequest().body("lead_id not found");
            }
            boolean ok = failureHandler.onSendFailed(leadId);
            return ok
                    ? ResponseEntity.ok("Lead " + leadId + " moved to 'нереализовано'")
                    : ResponseEntity.status(500).body("Failed to move lead " + leadId);
        } catch (Exception e) {
            log.error("fail-send-message webhook error", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    private Long extractLeadId(MultiValueMap<String, String> formData, String body) {
        // 1) form / query параметры
        if (formData != null) {
            for (String key : LEAD_ID_KEYS) {
                Long id = parseLong(formData.getFirst(key));
                if (id != null) {
                    return id;
                }
            }
        }
        if (body == null || body.isBlank()) {
            return null;
        }
        // 2) JSON-тело
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonObject json = JsonParser.parseString(trimmed).getAsJsonObject();
                for (String key : LEAD_ID_KEYS) {
                    if (json.has(key) && !json.get(key).isJsonNull()) {
                        Long id = parseLong(json.get(key).getAsString());
                        if (id != null) {
                            return id;
                        }
                    }
                }
            } catch (Exception ignored) {
                // не JSON — попробуем form-строку ниже
            }
        }
        // 3) form-строка в сыром теле (на случай неверного Content-Type)
        if (trimmed.contains("=")) {
            for (String pair : trimmed.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = pair.substring(0, eq).trim();
                if (LEAD_ID_KEYS.contains(key)) {
                    Long id = parseLong(pair.substring(eq + 1).trim());
                    if (id != null) {
                        return id;
                    }
                }
            }
        }
        return null;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
