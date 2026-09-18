package ru.anyforms.integration.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.anyforms.dto.cdek.CdekLocation;
import ru.anyforms.dto.cdek.CdekPackage;
import ru.anyforms.dto.cdek.CdekTariffQuote;
import ru.anyforms.exception.CdekCalculationException;
import ru.anyforms.integration.CdekCalculatorGateway;
import ru.anyforms.service.impl.CdekAuthService;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
class CdekCalculatorHttpGateway implements CdekCalculatorGateway {

    private static final Logger logger = LoggerFactory.getLogger(CdekCalculatorHttpGateway.class);
    private static final String TARIFF_URL = "https://api.cdek.ru/v2/calculator/tariff";
    private static final int ORDER_TYPE_ONLINE_SHOP = 1;
    private static final int CURRENCY_RUB = 1;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final CdekAuthService cdekAuthService;
    private final Gson gson = new Gson();
    private final WebClient webClient = WebClient.builder().build();

    @Override
    public CdekTariffQuote calculate(int tariffCode, CdekLocation from, CdekLocation to, List<CdekPackage> packages) {
        String token = cdekAuthService.getAccessToken();
        if (token == null) {
            throw new CdekCalculationException("Не удалось получить токен доступа СДЭК");
        }
        String requestJson = gson.toJson(buildRequest(tariffCode, from, to, packages));
        logger.info("Калькулятор СДЭК: запрос {}", requestJson);
        String body;
        try {
            body = webClient.post()
                    .uri(TARIFF_URL)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            logger.error("Калькулятор СДЭК: HTTP {}, ответ {}", e.getStatusCode().value(), errorBody);
            throw new CdekCalculationException("Ошибка API СДЭК (HTTP " + e.getStatusCode().value() + "): "
                    + extractError(errorBody, e.getMessage()));
        } catch (Exception e) {
            logger.error("Калькулятор СДЭК: ошибка запроса: {}", e.getMessage());
            throw new CdekCalculationException("Ошибка обращения к СДЭК: " + e.getMessage());
        }
        if (body == null || body.isBlank()) {
            throw new CdekCalculationException("Пустой ответ калькулятора СДЭК");
        }
        logger.info("Калькулятор СДЭК: ответ {}", body);
        return parse(body);
    }

    static JsonObject buildRequest(int tariffCode, CdekLocation from, CdekLocation to, List<CdekPackage> packages) {
        JsonObject request = new JsonObject();
        request.addProperty("type", ORDER_TYPE_ONLINE_SHOP);
        request.addProperty("currency", CURRENCY_RUB);
        request.addProperty("tariff_code", tariffCode);
        request.add("from_location", location(from));
        request.add("to_location", location(to));
        JsonArray packagesJson = new JsonArray();
        for (CdekPackage p : packages) {
            JsonObject pkg = new JsonObject();
            pkg.addProperty("weight", p.weightGrams());
            pkg.addProperty("length", p.lengthCm());
            pkg.addProperty("width", p.widthCm());
            pkg.addProperty("height", p.heightCm());
            packagesJson.add(pkg);
        }
        request.add("packages", packagesJson);
        return request;
    }

    private static JsonObject location(CdekLocation location) {
        JsonObject json = new JsonObject();
        addIfPresent(json, "country_code", location.countryCode());
        addIfPresent(json, "postal_code", location.postalCode());
        addIfPresent(json, "city", location.city());
        addIfPresent(json, "address", location.address());
        return json;
    }

    private static void addIfPresent(JsonObject json, String key, String value) {
        if (value != null && !value.isBlank()) {
            json.addProperty(key, value);
        }
    }

    private CdekTariffQuote parse(String body) {
        JsonObject json = gson.fromJson(body, JsonObject.class);
        String error = firstError(json);
        if (error != null) {
            throw new CdekCalculationException(error);
        }
        if (!json.has("delivery_sum")) {
            throw new CdekCalculationException("В ответе СДЭК нет стоимости доставки");
        }
        return new CdekTariffQuote(
                json.get("delivery_sum").getAsBigDecimal(),
                intOrNull(json.get("period_min")),
                intOrNull(json.get("period_max")));
    }

    private String extractError(String errorBody, String fallback) {
        if (errorBody == null || errorBody.isBlank()) {
            return fallback;
        }
        try {
            String error = firstError(gson.fromJson(errorBody, JsonObject.class));
            return error != null ? error : errorBody;
        } catch (Exception e) {
            return errorBody;
        }
    }

    private static String firstError(JsonObject json) {
        if (json == null) {
            return null;
        }
        if (json.has("errors") && json.get("errors").isJsonArray()) {
            JsonArray errors = json.getAsJsonArray("errors");
            if (!errors.isEmpty()) {
                JsonObject first = errors.get(0).getAsJsonObject();
                if (first.has("message")) {
                    return first.get("message").getAsString();
                }
                if (first.has("code")) {
                    return "Код ошибки СДЭК: " + first.get("code").getAsString();
                }
            }
        }
        if (json.has("message")) {
            return json.get("message").getAsString();
        }
        return null;
    }

    private static Integer intOrNull(JsonElement element) {
        return element == null || element.isJsonNull() ? null : element.getAsInt();
    }
}
