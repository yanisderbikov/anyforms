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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Вебхуки SalesBot-дрипа от amoCRM.
 * <p>
 * {@code POST /webhook/amocrm/fail-send-message} — amoCRM сообщает, что бот не смог
 * отправить сообщение лиду; переносим лид(ов) в «нереализовано».
 * <p>
 * lead_id извлекается из:
 * <ul>
 *   <li>стандартного формата amo-вебхука: {@code leads[add|status|update][N][id]} (основной случай);</li>
 *   <li>плоских ключей: {@code lead_id} / {@code leadId} / {@code entity_id} / {@code element_id};</li>
 * </ul>
 * — как из form/query-параметров, так и из JSON- или form-тела. В одном вебхуке может быть
 * несколько лидов — переносим всех.
 */
@RestController
@RequestMapping("/webhook/amocrm")
@Slf4j
public class SalesbotWebhookController {

    private static final List<String> FLAT_LEAD_ID_KEYS = List.of("lead_id", "leadId", "entity_id", "element_id");

    /** Ключи стандартного amo-вебхука вида leads[add][0][id], leads[status][1][id], leads[update][0][id]. */
    private static final Pattern AMO_LEAD_ID_KEY = Pattern.compile("^leads\\[[a-zA-Z_]+]\\[\\d+]\\[id]$");

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
            List<Long> leadIds = extractLeadIds(formData, body);
            if (leadIds.isEmpty()) {
                log.warn("fail-send-message: lead_id не найден. form={}, body={}", formData, body);
                return ResponseEntity.badRequest().body("lead_id not found");
            }
            int moved = 0;
            for (Long leadId : leadIds) {
                if (failureHandler.onSendFailed(leadId)) {
                    moved++;
                }
            }
            return ResponseEntity.ok("moved " + moved + "/" + leadIds.size() + " lead(s) to 'нереализовано'");
        } catch (Exception e) {
            log.error("fail-send-message webhook error", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    private List<Long> extractLeadIds(MultiValueMap<String, String> formData, String body) {
        Set<Long> ids = new LinkedHashSet<>();

        // 1) form / query параметры (Spring уже распарсил form-urlencoded тело сюда)
        if (formData != null) {
            formData.forEach((key, values) -> {
                if (isLeadIdKey(key)) {
                    values.forEach(v -> addId(ids, v));
                }
            });
        }

        if (!ids.isEmpty() || body == null || body.isBlank()) {
            return new ArrayList<>(ids);
        }

        // 2) JSON-тело: плоские ключи
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonObject json = JsonParser.parseString(trimmed).getAsJsonObject();
                for (String key : FLAT_LEAD_ID_KEYS) {
                    if (json.has(key) && !json.get(key).isJsonNull()) {
                        addId(ids, json.get(key).getAsString());
                    }
                }
                return new ArrayList<>(ids);
            } catch (Exception ignored) {
                // не JSON — пробуем form-строку ниже
            }
        }

        // 3) form-строка в сыром теле (на случай неверного Content-Type) — с url-декодом ключей
        if (trimmed.contains("=")) {
            for (String pair : trimmed.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8).trim();
                if (isLeadIdKey(key)) {
                    addId(ids, URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private static boolean isLeadIdKey(String key) {
        return FLAT_LEAD_ID_KEYS.contains(key) || AMO_LEAD_ID_KEY.matcher(key).matches();
    }

    private static void addId(Set<Long> ids, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            ids.add(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            // не число — пропускаем
        }
    }
}
