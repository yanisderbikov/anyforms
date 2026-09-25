package ru.anyforms.integration.impl;

import ru.anyforms.dto.cdek.CdekLocation;
import ru.anyforms.dto.cdek.CdekOrderInfo;
import ru.anyforms.dto.cdek.CdekPackage;
import ru.anyforms.integration.CdekTrackingGateway;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
class CdekTrackingHttpGateway implements CdekTrackingGateway {
    private static final Logger logger = LoggerFactory.getLogger(CdekTrackingHttpGateway.class);
    private static final String CDEK_ORDERS_URL = "https://api.cdek.ru/v2/orders";
    private static final String CDEK_AUTH_URL = "https://api.cdek.ru/v2/oauth/token";
    
    private final WebClient webClient;
    private final Gson gson;
    
    @Value("${sdek.secret.key}")
    private String sdekSecretKey;
    
    @Value("${sdek.client.id}")
    private String sdekClientId;
    
    // Кэш для токена доступа
    private String accessToken;
    private Instant tokenExpiresAt;

    public CdekTrackingHttpGateway() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.gson = new Gson();
    }

    @Override
    public String checkTrackingStatus(String trackingNumber) {
        try {
            logger.info("Проверка статуса трекера СДЭК: {}", trackingNumber);
            
            // Очищаем номер трекера от пробелов и дефисов
            String cleanTrackingNumber = trackingNumber.trim().replaceAll("[\\s-]", "");
            
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.cdek.ru")
                            .path("/v2/orders")
                            .queryParam("cdek_number", cleanTrackingNumber)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && !response.isEmpty()) {
                return parseOrdersResponse(response, cleanTrackingNumber);
            }
            
            logger.warn("Не удалось получить статус для трекера СДЭК: {}", trackingNumber);
            return "Статус недоступен";
            
        } catch (Exception e) {
            logger.error("Ошибка при проверке статуса трекера СДЭК {}: {}", trackingNumber, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public String getAccessToken() {
        // Проверяем, есть ли валидный токен в кэше
        if (accessToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }
        
        try {
            logger.debug("Получение токена доступа СДЭК через OAuth 2.0");
            
            // Если client_id не указан, используем secret.key как client_secret
            // и пробуем использовать secret.key как client_id (иногда это один и тот же ключ)
            String clientId = (sdekClientId != null && !sdekClientId.isEmpty()) 
                    ? sdekClientId 
                    : sdekSecretKey;
            String clientSecret = sdekSecretKey;
            
            if (clientSecret == null || clientSecret.isEmpty()) {
                logger.warn("Секретный ключ СДЭК не настроен");
                return null;
            }
            
            // Формируем тело запроса для OAuth
            String requestBody = String.format(
                    "grant_type=client_credentials&client_id=%s&client_secret=%s",
                    clientId, clientSecret
            );
            
            String response = webClient.post()
                    .uri(CDEK_AUTH_URL)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            if (response != null && !response.isEmpty()) {
                JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                
                if (jsonResponse.has("access_token")) {
                    accessToken = jsonResponse.get("access_token").getAsString();
                    
                    // Получаем время истечения токена (обычно 3600 секунд)
                    int expiresIn = jsonResponse.has("expires_in") 
                            ? jsonResponse.get("expires_in").getAsInt() 
                            : 3600;
                    
                    // Устанавливаем время истечения с запасом в 60 секунд
                    tokenExpiresAt = Instant.now().plusSeconds(expiresIn - 60);
                    
                    logger.debug("Токен доступа СДЭК успешно получен, действителен до {}", tokenExpiresAt);
                    return accessToken;
                } else {
                    logger.error("Ошибка получения токена СДЭК: {}", response);
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при получении токена доступа СДЭК: {}", e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Парсит ответ от API /v2/orders
     * Извлекает последний статус из массива statuses
     */
    private String parseOrdersResponse(String response, String trackingNumber) {
        try {
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            
            if (!jsonResponse.has("entity")) {
                logger.warn("Ответ не содержит entity для трекера {}", trackingNumber);
                return "Статус недоступен";
            }
            
            JsonObject entity = jsonResponse.getAsJsonObject("entity");
            
            // Извлекаем информацию из массива статусов
            if (entity.has("statuses") && entity.get("statuses").isJsonArray()) {
                JsonArray statuses = entity.getAsJsonArray("statuses");
                if (statuses.size() > 0) {
                    // Первый элемент в массиве - это последний статус
                    JsonObject lastStatus = statuses.get(0).getAsJsonObject();
                    
                    StringBuilder result = new StringBuilder();
                    
                    if (lastStatus.has("name")) {
                        result.append(lastStatus.get("name").getAsString());
                    }
                    
                    if (lastStatus.has("date_time")) {
                        String dateTime = lastStatus.get("date_time").getAsString();
                        if (result.length() > 0) {
                            result.append(" (").append(formatDateTime(dateTime)).append(")");
                        } else {
                            result.append(formatDateTime(dateTime));
                        }
                    }
                    
                    if (result.length() > 0) {
                        logger.info("Статус трекера {}: {}", trackingNumber, result.toString());
                        return result.toString();
                    }
                }
            }
            
            logger.warn("Не удалось извлечь статус из ответа для трекера {}", trackingNumber);
            return "Статус недоступен";
            
        } catch (Exception e) {
            logger.error("Ошибка парсинга ответа СДЭК для трекера {}: {}", trackingNumber, e.getMessage(), e);
            return "Ошибка при получении статуса";
        }
    }
    
    /**
     * Форматирует дату и время из ISO формата в читаемый вид
     */
    private String formatDateTime(String dateTimeStr) {
        try {
            // Убираем временную зону и парсим дату
            String dateTime = dateTimeStr.replaceAll("\\+\\d{4}$", "");
            String[] parts = dateTime.split("T");
            if (parts.length == 2) {
                String date = parts[0]; // yyyy-MM-dd
                String time = parts[1]; // HH:mm:ss
                
                String[] dateParts = date.split("-");
                if (dateParts.length == 3) {
                    String formattedDate = dateParts[2] + "." + dateParts[1] + "." + dateParts[0];
                    
                    // Берем только часы и минуты
                    String[] timeParts = time.split(":");
                    if (timeParts.length >= 2) {
                        return formattedDate + " " + timeParts[0] + ":" + timeParts[1];
                    }
                    return formattedDate;
                }
            }
        } catch (Exception e) {
            logger.debug("Ошибка форматирования даты {}: {}", dateTimeStr, e.getMessage());
        }
        
        // Если не удалось отформатировать, возвращаем как есть
        return dateTimeStr;
    }

    @Override
    public String getOrderStatus(String trackingNumber) {
        try {
            logger.debug("Получение кода статуса для трекера СДЭК: {}", trackingNumber);

            String cleanTrackingNumber = trackingNumber.trim().replaceAll("[\\s-]", "");
            
            String token = getAccessToken();
            if (token == null) {
                logger.warn("Не удалось получить токен доступа для трекера {}", trackingNumber);
                return null;
            }
            
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.cdek.ru")
                            .path("/v2/orders")
                            .queryParam("cdek_number", cleanTrackingNumber)
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .onStatus(status -> status.value() == 400, clientResponse -> {
                        logger.info("Заказ {} не найден (ошибка 400), возвращаем статус {}", 
                                ru.anyforms.model.CdekOrderStatus.NOT_FOUND_OR_DELIVERED.getCode(), cleanTrackingNumber);
                        return Mono.error(new RuntimeException(ru.anyforms.model.CdekOrderStatus.NOT_FOUND_OR_DELIVERED.getCode()));
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && !response.isEmpty()) {
                return extractStatusCode(response, cleanTrackingNumber);
            }
            
            logger.warn("Не удалось получить код статуса для трекера СДЭК: {}", trackingNumber);
            return null;
            
        } catch (RuntimeException e) {
            if (ru.anyforms.model.CdekOrderStatus.NOT_FOUND_OR_DELIVERED.getCode().equals(e.getMessage())) {
                return ru.anyforms.model.CdekOrderStatus.NOT_FOUND_OR_DELIVERED.getCode();
            }
            logger.error("Ошибка при получении кода статуса трекера СДЭК {}: {}", trackingNumber, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Ошибка при получении кода статуса трекера СДЭК {}: {}", trackingNumber, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Извлекает код статуса из ответа API /v2/orders
     */
    private String extractStatusCode(String response, String trackingNumber) {
        try {
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            
            if (!jsonResponse.has("entity")) {
                logger.warn("Ответ не содержит entity для трекера {}", trackingNumber);
                return null;
            }
            
                JsonObject entity = jsonResponse.getAsJsonObject("entity");
            
            // Извлекаем код статуса из массива статусов
            if (entity.has("statuses") && entity.get("statuses").isJsonArray()) {
                JsonArray statuses = entity.getAsJsonArray("statuses");
                if (statuses.size() > 0) {
                    // Первый элемент в массиве - это последний статус
                    JsonObject lastStatus = statuses.get(0).getAsJsonObject();
                    
                    if (lastStatus.has("code")) {
                        return lastStatus.get("code").getAsString();
                    }
                }
            }
            
            logger.warn("Не удалось извлечь код статуса из ответа для трекера {}", trackingNumber);
                return null;
            
        } catch (Exception e) {
            logger.error("Ошибка извлечения кода статуса для трекера {}: {}", trackingNumber, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public CdekOrderInfo getOrderInfo(String trackingNumber) {
        try {
            String cleanTrackingNumber = trackingNumber.trim().replaceAll("[\\s-]", "");
            String token = getAccessToken();
            if (token == null) {
                logger.warn("Не удалось получить токен доступа для данных заказа трекера {}", trackingNumber);
                return null;
            }
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.cdek.ru")
                            .path("/v2/orders")
                            .queryParam("cdek_number", cleanTrackingNumber)
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            CdekOrderInfo info = parseOrderInfo(response);
            if (info == null) {
                logger.warn("СДЭК не отдал данные заказа для трекера {}", trackingNumber);
            } else if (info.plannedDeliveryDate() == null) {
                logger.info("СДЭК не отдал плановую дату доставки для трекера {}", trackingNumber);
            } else {
                logger.info("Плановая дата доставки трекера {}: {}", trackingNumber, info.plannedDeliveryDate());
            }
            return info;
        } catch (Exception e) {
            logger.error("Ошибка при получении данных заказа трекера СДЭК {}: {}", trackingNumber, e.getMessage(), e);
            return null;
        }
    }

    static CdekOrderInfo parseOrderInfo(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        JsonObject json = new Gson().fromJson(response, JsonObject.class);
        if (json == null || !json.has("entity") || !json.get("entity").isJsonObject()) {
            return null;
        }
        JsonObject entity = json.getAsJsonObject("entity");
        return new CdekOrderInfo(
                parseDate(stringOrNull(entity, "planned_delivery_date")),
                intOrNull(entity, "tariff_code"),
                parseLocation(entity, "from_location"),
                parseLocation(entity, "to_location"),
                parsePackages(entity));
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException e) {
            logger.warn("Не удалось разобрать плановую дату доставки СДЭК: {}", raw);
            return null;
        }
    }

    private static CdekLocation parseLocation(JsonObject entity, String key) {
        if (!entity.has(key) || !entity.get(key).isJsonObject()) {
            return null;
        }
        JsonObject location = entity.getAsJsonObject(key);
        String postalCode = stringOrNull(location, "postal_code");
        String city = stringOrNull(location, "city");
        String address = stringOrNull(location, "address");
        if (postalCode == null && city == null && address == null) {
            return null;
        }
        String country = stringOrNull(location, "country_code");
        return new CdekLocation(country != null ? country : "RU", postalCode, city, address);
    }

    private static List<CdekPackage> parsePackages(JsonObject entity) {
        if (!entity.has("packages") || !entity.get("packages").isJsonArray()) {
            return List.of();
        }
        List<CdekPackage> packages = new ArrayList<>();
        for (JsonElement element : entity.getAsJsonArray("packages")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject pkg = element.getAsJsonObject();
            Integer weight = intOrNull(pkg, "weight");
            Integer length = intOrNull(pkg, "length");
            Integer width = intOrNull(pkg, "width");
            Integer height = intOrNull(pkg, "height");
            if (weight == null || weight <= 0) {
                continue;
            }
            packages.add(new CdekPackage(weight, positive(length), positive(width), positive(height)));
        }
        return packages;
    }

    private static int positive(Integer value) {
        return value == null || value <= 0 ? 1 : value;
    }

    private static String stringOrNull(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        String value = json.get(key).getAsString();
        return value.isBlank() ? null : value;
    }

    private static Integer intOrNull(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        try {
            return json.get(key).getAsInt();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isValidTrackingNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        // Трекер СДЭК обычно состоит из цифр (может быть с дефисами)
        String cleaned = value.trim().replaceAll("[\\s-]", "");
        return cleaned.matches("\\d{8,14}"); // От 8 до 14 цифр
    }
}
