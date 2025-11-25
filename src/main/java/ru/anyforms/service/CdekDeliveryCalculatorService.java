package ru.anyforms.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;

@Service
public class CdekDeliveryCalculatorService {
    private static final Logger logger = LoggerFactory.getLogger(CdekDeliveryCalculatorService.class);
    private static final String CDEK_CALCULATOR_URL = "https://api.cdek.ru/v2/calculator/tarifflist";
    private static final String CDEK_AUTH_URL = "https://api.cdek.ru/v2/oauth/token";
    
    private final WebClient webClient;
    private final Gson gson;
    
    @Value("${sdek.secret.key:}")
    private String sdekSecretKey;
    
    @Value("${sdek.client.id:}")
    private String sdekClientId;
    
    // Кэш для токена доступа
    private String accessToken;
    private Instant tokenExpiresAt;
    
    // Адрес отправки (ПВЗ)
    private static final String FROM_LOCATION = "Санкт-Петербург";
    private static final String FROM_ADDRESS = "ул. Трефолева, 9, корп. 2";
    private static final int FROM_POSTAL_CODE = 192076; // Примерный почтовый индекс для этого адреса
    
    public CdekDeliveryCalculatorService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.gson = new Gson();
    }
    
    /**
     * Результат расчета доставки
     */
    public static class DeliveryCalculationResult {
        private final Double cost;
        private final Integer deliveryPeriodMin;
        private final Integer deliveryPeriodMax;
        private final String error;
        
        public DeliveryCalculationResult(Double cost, Integer deliveryPeriodMin, Integer deliveryPeriodMax) {
            this.cost = cost;
            this.deliveryPeriodMin = deliveryPeriodMin;
            this.deliveryPeriodMax = deliveryPeriodMax;
            this.error = null;
        }
        
        public DeliveryCalculationResult(String error) {
            this.cost = null;
            this.deliveryPeriodMin = null;
            this.deliveryPeriodMax = null;
            this.error = error;
        }
        
        public boolean isSuccess() {
            return error == null;
        }
        
        public Double getCost() {
            return cost;
        }
        
        public Integer getDeliveryPeriodMin() {
            return deliveryPeriodMin;
        }
        
        public Integer getDeliveryPeriodMax() {
            return deliveryPeriodMax;
        }
        
        public String getError() {
            return error;
        }
        
        public String getFormattedResult() {
            if (!isSuccess()) {
                return "Ошибка расчета: " + error;
            }
            
            StringBuilder result = new StringBuilder();
            result.append("Стоимость доставки: ").append(cost != null ? String.format("%.2f", cost) : "не указана").append(" руб.");
            
            if (deliveryPeriodMin != null && deliveryPeriodMax != null) {
                if (deliveryPeriodMin.equals(deliveryPeriodMax)) {
                    result.append("\nСрок доставки: ").append(deliveryPeriodMin).append(" дн.");
                } else {
                    result.append("\nСрок доставки: ").append(deliveryPeriodMin).append("-").append(deliveryPeriodMax).append(" дн.");
                }
            } else if (deliveryPeriodMin != null) {
                result.append("\nСрок доставки: от ").append(deliveryPeriodMin).append(" дн.");
            } else if (deliveryPeriodMax != null) {
                result.append("\nСрок доставки: до ").append(deliveryPeriodMax).append(" дн.");
            }
            
            return result.toString();
        }
    }
    
    /**
     * Получает токен доступа через OAuth 2.0
     */
    private String getAccessToken() {
        // Проверяем, есть ли валидный токен в кэше
        if (accessToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }
        
        try {
            logger.info("Получение токена доступа СДЭК через OAuth 2.0");
            
            String clientId = (sdekClientId != null && !sdekClientId.isEmpty()) 
                    ? sdekClientId 
                    : sdekSecretKey;
            String clientSecret = sdekSecretKey;
            
            if (clientSecret == null || clientSecret.isEmpty()) {
                logger.warn("Секретный ключ СДЭК не настроен");
                return null;
            }
            
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
                    
                    int expiresIn = jsonResponse.has("expires_in") 
                            ? jsonResponse.get("expires_in").getAsInt() 
                            : 3600;
                    
                    tokenExpiresAt = Instant.now().plusSeconds(expiresIn - 60);
                    
                    logger.info("Токен доступа СДЭК успешно получен");
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
     * Рассчитывает стоимость и время доставки
     * @param toCity город получателя
     * @param toPostalCode почтовый индекс получателя (опционально)
     * @param weight вес в граммах
     * @param length длина в см
     * @param width ширина в см
     * @param height высота в см
     * @return результат расчета
     */
    public DeliveryCalculationResult calculateDelivery(
            String toCity, 
            Integer toPostalCode,
            int weight, 
            int length, 
            int width, 
            int height) {
        try {
            logger.info("Расчет доставки СДЭК: город={}, вес={}г, размеры={}x{}x{}см", 
                    toCity, weight, length, width, height);
            
            String token = getAccessToken();
            if (token == null) {
                return new DeliveryCalculationResult("Не удалось получить токен доступа СДЭК");
            }
            
            // Формируем запрос для калькулятора
            JsonObject request = new JsonObject();
            
            // Адрес отправки (Санкт-Петербург)
            JsonObject fromLocation = new JsonObject();
            fromLocation.addProperty("code", FROM_POSTAL_CODE);
            fromLocation.addProperty("city", FROM_LOCATION);
            request.add("from_location", fromLocation);
            
            // Адрес получения
            JsonObject toLocation = new JsonObject();
            if (toPostalCode != null) {
                toLocation.addProperty("code", toPostalCode);
            }
            if (toCity != null && !toCity.isEmpty()) {
                toLocation.addProperty("city", toCity);
            }
            if (toPostalCode == null && (toCity == null || toCity.isEmpty())) {
                return new DeliveryCalculationResult("Не указан город или почтовый индекс получателя");
            }
            request.add("to_location", toLocation);
            
            // Пакет
            JsonArray packages = new JsonArray();
            JsonObject packageObj = new JsonObject();
            packageObj.addProperty("weight", weight); // вес в граммах
            packageObj.addProperty("length", length);
            packageObj.addProperty("width", width);
            packageObj.addProperty("height", height);
            packages.add(packageObj);
            request.add("packages", packages);
            
            // Тип тарифа (1 - стандартный, 2 - экспресс, 3 - экономичный)
            // Используем стандартный тариф
            // Также можно указать массив тарифов для получения всех доступных вариантов
            JsonArray tariffCodes = new JsonArray();
            tariffCodes.add(1); // Стандартный тариф
            request.add("tariff_codes", tariffCodes);
            
            String requestJson = gson.toJson(request);
            logger.info("Запрос к калькулятору СДЭК: {}", requestJson);
            
            String response = null;
            try {
                response = webClient.post()
                        .uri(CDEK_CALCULATOR_URL)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .bodyValue(requestJson)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(30))
                        .block();
            } catch (WebClientResponseException e) {
                // Получаем тело ответа при ошибке HTTP
                String errorResponseBody = e.getResponseBodyAsString();
                int statusCode = e.getStatusCode().value();
                
                logger.error("Ошибка HTTP при запросе к калькулятору СДЭК. Статус: {}, URL: {}", 
                        statusCode, CDEK_CALCULATOR_URL);
                logger.error("Запрос к СДЭК был: {}", requestJson);
                logger.error("Полный JSON ответ от СДЭК при ошибке: {}", 
                        errorResponseBody != null ? errorResponseBody : "null");
                
                // Пытаемся распарсить ошибку из ответа
                if (errorResponseBody != null && !errorResponseBody.isEmpty()) {
                    try {
                        JsonObject errorJson = gson.fromJson(errorResponseBody, JsonObject.class);
                        String errorMessage = extractErrorMessage(errorJson);
                        return new DeliveryCalculationResult(
                                String.format("Ошибка API СДЭК (HTTP %d): %s", statusCode, errorMessage));
                    } catch (Exception parseEx) {
                        logger.debug("Не удалось распарсить JSON ошибки: {}", parseEx.getMessage());
                    }
                }
                
                return new DeliveryCalculationResult(
                        String.format("Ошибка API СДЭК (HTTP %d): %s", statusCode, e.getMessage()));
            }
            
            if (response == null || response.isEmpty()) {
                logger.error("Пустой ответ от API СДЭК. Запрос был: {}", requestJson);
                return new DeliveryCalculationResult("Пустой ответ от API СДЭК");
            }
            
            logger.info("Ответ от калькулятора СДЭК: {}", response);
            
            DeliveryCalculationResult result = parseCalculationResponse(response);
            
            if (!result.isSuccess()) {
                logger.warn("Ошибка при парсинге ответа СДЭК. Ответ был: {}", response);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Неожиданная ошибка при расчете доставки СДЭК: {}", e.getMessage(), e);
            logger.error("Stack trace:", e);
            return new DeliveryCalculationResult("Ошибка при расчете: " + e.getMessage());
        }
    }
    
    /**
     * Парсит ответ от калькулятора СДЭК
     */
    private DeliveryCalculationResult parseCalculationResponse(String response) {
        try {
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            
            // Проверяем наличие ошибок
            if (jsonResponse.has("errors") && jsonResponse.get("errors").isJsonArray()) {
                JsonArray errors = jsonResponse.getAsJsonArray("errors");
                if (errors.size() > 0) {
                    JsonObject firstError = errors.get(0).getAsJsonObject();
                    String errorMessage = firstError.has("message") 
                            ? firstError.get("message").getAsString() 
                            : "Неизвестная ошибка";
                    return new DeliveryCalculationResult(errorMessage);
                }
            }
            
            // Ищем тарифы в ответе
            if (jsonResponse.has("tariff_codes") && jsonResponse.get("tariff_codes").isJsonArray()) {
                JsonArray tariffCodes = jsonResponse.getAsJsonArray("tariff_codes");
                
                if (tariffCodes.size() > 0) {
                    // Берем первый доступный тариф
                    JsonObject tariff = tariffCodes.get(0).getAsJsonObject();
                    
                    Double cost = null;
                    Integer deliveryPeriodMin = null;
                    Integer deliveryPeriodMax = null;
                    
                    if (tariff.has("delivery_sum")) {
                        cost = tariff.get("delivery_sum").getAsDouble();
                    }
                    
                    if (tariff.has("period_min")) {
                        deliveryPeriodMin = tariff.get("period_min").getAsInt();
                    }
                    
                    if (tariff.has("period_max")) {
                        deliveryPeriodMax = tariff.get("period_max").getAsInt();
                    }
                    
                    if (cost != null || deliveryPeriodMin != null || deliveryPeriodMax != null) {
                        return new DeliveryCalculationResult(cost, deliveryPeriodMin, deliveryPeriodMax);
                    }
                }
            }
            
            // Альтернативный формат ответа (если тарифы в корне)
            if (jsonResponse.has("delivery_sum") || jsonResponse.has("period_min")) {
                Double cost = jsonResponse.has("delivery_sum") 
                        ? jsonResponse.get("delivery_sum").getAsDouble() 
                        : null;
                Integer deliveryPeriodMin = jsonResponse.has("period_min") 
                        ? jsonResponse.get("period_min").getAsInt() 
                        : null;
                Integer deliveryPeriodMax = jsonResponse.has("period_max") 
                        ? jsonResponse.get("period_max").getAsInt() 
                        : null;
                
                return new DeliveryCalculationResult(cost, deliveryPeriodMin, deliveryPeriodMax);
            }
            
            return new DeliveryCalculationResult("Не удалось распарсить ответ от API СДЭК: " + response);
            
        } catch (Exception e) {
            logger.error("Ошибка парсинга ответа калькулятора СДЭК: {}", e.getMessage(), e);
            return new DeliveryCalculationResult("Ошибка парсинга ответа: " + e.getMessage());
        }
    }
    
    /**
     * Извлекает сообщение об ошибке из JSON ответа СДЭК
     */
    private String extractErrorMessage(JsonObject errorJson) {
        try {
            // Пробуем разные варианты структуры ошибки
            if (errorJson.has("errors") && errorJson.get("errors").isJsonArray()) {
                JsonArray errors = errorJson.getAsJsonArray("errors");
                if (errors.size() > 0) {
                    JsonObject firstError = errors.get(0).getAsJsonObject();
                    if (firstError.has("message")) {
                        return firstError.get("message").getAsString();
                    }
                    if (firstError.has("code")) {
                        return "Код ошибки: " + firstError.get("code").getAsString();
                    }
                }
            }
            
            if (errorJson.has("error")) {
                String error = errorJson.get("error").getAsString();
                if (errorJson.has("error_description")) {
                    return error + ": " + errorJson.get("error_description").getAsString();
                }
                return error;
            }
            
            if (errorJson.has("message")) {
                return errorJson.get("message").getAsString();
            }
            
            // Если не нашли структурированное сообщение, возвращаем весь JSON
            return errorJson.toString();
            
        } catch (Exception e) {
            logger.debug("Ошибка при извлечении сообщения об ошибке: {}", e.getMessage());
            return errorJson.toString();
        }
    }
}

