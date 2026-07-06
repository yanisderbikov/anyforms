package ru.anyforms.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import ru.anyforms.dto.cdek.CdekPvzDTO;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Саджест ПВЗ СДЭК «как в Яндексе»: пользователь пишет улицу/город («Грибоедова»),
 * в ответ — все подходящие пункты по всей России («ул. Грибоедова 135, г. Пермь» и т.д.).
 * У API СДЭК нет текстового поиска по deliverypoints (только city_code/postal_code и структурные
 * фильтры), поэтому полный список ПВЗ страны кэшируется в памяти и фильтруется подстрокой.
 */
@Service
@RequiredArgsConstructor
public class CdekPvzService {

    private static final Logger logger = LoggerFactory.getLogger(CdekPvzService.class);
    private static final String DELIVERY_POINTS_URL = "https://api.cdek.ru/v2/deliverypoints";
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int MAX_RESULTS = 50;

    private final CdekAuthService cdekAuthService;
    // Полный список ПВЗ России — десятки мегабайт JSON, буфер с запасом.
    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(256 * 1024 * 1024))
            .build();
    private final Gson gson = new Gson();

    private volatile List<CdekPvzDTO> cache = List.of();
    private volatile Instant cacheLoadedAt;

    /**
     * @param query улица/город/часть адреса, слова через пробел или запятую («грибоедова 135 пермь»).
     * @return до {@value #MAX_RESULTS} ПВЗ, у которых адрес/город/название содержат все слова запроса.
     */
    public List<CdekPvzDTO> search(String query) {
        if (query == null || query.trim().length() < 3) {
            return List.of();
        }
        List<CdekPvzDTO> points = getAllPoints();
        if (points.isEmpty()) {
            return List.of();
        }

        String[] tokens = query.trim().toLowerCase().split("[\\s,]+");
        List<CdekPvzDTO> matched = new ArrayList<>();
        for (CdekPvzDTO p : points) {
            if (matches(p, tokens)) {
                matched.add(p);
            }
        }
        // Стабильный порядок: сначала по городу, внутри города — по адресу.
        matched.sort(Comparator
                .comparing((CdekPvzDTO p) -> p.getCity() == null ? "" : p.getCity())
                .thenComparing(p -> p.getAddress() == null ? "" : p.getAddress()));
        return matched.size() > MAX_RESULTS ? matched.subList(0, MAX_RESULTS) : matched;
    }

    private boolean matches(CdekPvzDTO p, String[] tokens) {
        String haystack = ((p.getAddress() == null ? "" : p.getAddress()) + " "
                + (p.getFullAddress() == null ? "" : p.getFullAddress()) + " "
                + (p.getCity() == null ? "" : p.getCity()) + " "
                + (p.getName() == null ? "" : p.getName())).toLowerCase();
        for (String token : tokens) {
            if (!token.isBlank() && !haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private List<CdekPvzDTO> getAllPoints() {
        if (cacheLoadedAt != null && Instant.now().isBefore(cacheLoadedAt.plus(CACHE_TTL)) && !cache.isEmpty()) {
            return cache;
        }
        synchronized (this) {
            if (cacheLoadedAt != null && Instant.now().isBefore(cacheLoadedAt.plus(CACHE_TTL)) && !cache.isEmpty()) {
                return cache;
            }
            List<CdekPvzDTO> fresh = loadAllPoints();
            if (!fresh.isEmpty()) {
                cache = fresh;
                cacheLoadedAt = Instant.now();
            }
            // При ошибке загрузки продолжаем отдавать устаревший кэш, если он есть.
            return cache;
        }
    }

    private List<CdekPvzDTO> loadAllPoints() {
        String token = cdekAuthService.getAccessToken();
        if (token == null) {
            logger.warn("ПВЗ СДЭК: не удалось получить токен доступа");
            return List.of();
        }
        try {
            long started = System.currentTimeMillis();
            String response = webClient.get()
                    .uri(DELIVERY_POINTS_URL + "?type=PVZ&country_code=RU")
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            if (response == null || response.isBlank()) {
                return List.of();
            }
            JsonArray points = gson.fromJson(response, JsonArray.class);
            if (points == null) {
                return List.of();
            }
            List<CdekPvzDTO> result = new ArrayList<>(points.size());
            for (JsonElement el : points) {
                CdekPvzDTO dto = mapPoint(el.getAsJsonObject());
                if (dto != null) {
                    result.add(dto);
                }
            }
            logger.info("ПВЗ СДЭК: загружено {} пунктов за {} мс", result.size(), System.currentTimeMillis() - started);
            return result;
        } catch (Exception e) {
            logger.error("ПВЗ СДЭК: ошибка загрузки полного списка: {}", e.getMessage());
            return List.of();
        }
    }

    private CdekPvzDTO mapPoint(JsonObject point) {
        String code = asString(point, "code");
        if (code == null) {
            return null;
        }
        JsonObject location = point.has("location") && point.get("location").isJsonObject()
                ? point.getAsJsonObject("location")
                : null;
        return CdekPvzDTO.builder()
                .code(code)
                .name(asString(point, "name"))
                .workTime(asString(point, "work_time"))
                .city(location != null ? asString(location, "city") : null)
                .address(location != null ? asString(location, "address") : null)
                .fullAddress(location != null ? asString(location, "address_full") : null)
                .longitude(location != null ? asDouble(location, "longitude") : null)
                .latitude(location != null ? asDouble(location, "latitude") : null)
                .build();
    }

    private static String asString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static Double asDouble(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsDouble() : null;
    }
}
