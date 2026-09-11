package ru.anyforms.integration.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.anyforms.dto.cdek.CdekPvzDTO;
import ru.anyforms.integration.CdekDeliveryPointsGateway;
import ru.anyforms.service.impl.CdekAuthService;

import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
class CdekDeliveryPointsHttpGateway implements CdekDeliveryPointsGateway {

    private static final Logger logger = LoggerFactory.getLogger(CdekDeliveryPointsHttpGateway.class);
    private static final String DELIVERY_POINTS_URL = "https://api.cdek.ru/v2/deliverypoints?type=PVZ";
    private static final int MAX_BODY_BYTES = 512 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    private final CdekAuthService cdekAuthService;
    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_BODY_BYTES))
            .build();

    @Override
    public List<CdekPvzDTO> fetchAllPickupPoints() {
        String token = cdekAuthService.getAccessToken();
        if (token == null) {
            logger.warn("ПВЗ СДЭК: не удалось получить токен доступа");
            return List.of();
        }
        try {
            long started = System.currentTimeMillis();
            String body = webClient.get()
                    .uri(DELIVERY_POINTS_URL)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            if (body == null || body.isBlank()) {
                logger.warn("ПВЗ СДЭК: пустой ответ deliverypoints");
                return List.of();
            }
            List<CdekPvzDTO> result = parse(body);
            logger.info("ПВЗ СДЭК: получено {} пунктов за {} мс", result.size(), System.currentTimeMillis() - started);
            return result;
        } catch (Exception e) {
            logger.error("ПВЗ СДЭК: ошибка загрузки полного списка: {}", e.getMessage());
            return List.of();
        }
    }

    private List<CdekPvzDTO> parse(String body) throws Exception {
        List<CdekPvzDTO> result = new ArrayList<>(65_536);
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            reader.beginArray();
            while (reader.hasNext()) {
                CdekPvzDTO dto = mapPoint(JsonParser.parseReader(reader).getAsJsonObject());
                if (dto != null) {
                    result.add(dto);
                }
            }
            reader.endArray();
        }
        return result;
    }

    private CdekPvzDTO mapPoint(JsonObject point) {
        String code = asString(point, "code");
        if (code == null) {
            return null;
        }
        JsonObject location = point.has("location") && point.get("location").isJsonObject()
                ? point.getAsJsonObject("location")
                : new JsonObject();
        return CdekPvzDTO.builder()
                .code(code)
                .name(asString(point, "name"))
                .workTime(asString(point, "work_time"))
                .countryCode(asString(location, "country_code"))
                .region(asString(location, "region"))
                .city(asString(location, "city"))
                .postalCode(asString(location, "postal_code"))
                .address(asString(location, "address"))
                .fullAddress(asString(location, "address_full"))
                .longitude(asDouble(location, "longitude"))
                .latitude(asDouble(location, "latitude"))
                .build();
    }

    private static String asString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static Double asDouble(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsDouble() : null;
    }
}
