package ru.anyforms.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CdekTrackingService {
    private static final Logger logger = LoggerFactory.getLogger(CdekTrackingService.class);
    // Публичный API СДЭК для проверки трекеров
    private static final String CDEK_TRACKING_URL = "https://api.cdek.ru/v2/orders";
    private static final String CDEK_AUTH_URL = "https://api.cdek.ru/v2/oauth/token";
    private static final String CDEK_DELIVERY_INTERVALS_URL = "https://api.cdek.ru/v2/delivery/intervals";
    
    private final WebClient webClient;
    private final Gson gson;
    
    @Value("${sdek.secret.key:}")
    private String sdekSecretKey;
    
    @Value("${sdek.client.id:}")
    private String sdekClientId;
    
    // Кэш для токена доступа
    private String accessToken;
    private Instant tokenExpiresAt;

    public CdekTrackingService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.gson = new Gson();
    }

    /**
     * Проверяет статус трекера СДЭК
     * Сначала пробует через API с авторизацией, затем через публичный сайт
     * @param trackingNumber номер трекера
     * @return статус доставки или null в случае ошибки
     */
    public String checkTrackingStatus(String trackingNumber) {
        try {
            logger.info("Проверка статуса трекера СДЭК: {}", trackingNumber);
            
            // Очищаем номер трекера от пробелов и дефисов
            String cleanTrackingNumber = trackingNumber.trim().replaceAll("[\\s-]", "");
            
            // Сначала пробуем через API с авторизацией (если есть ключ)
            if (sdekSecretKey != null && !sdekSecretKey.isEmpty()) {
                String status = tryCdekApiV2WithAuth(cleanTrackingNumber);
                if (status != null && !status.isEmpty()) {
                    return status;
                }
            }
            
            // Если API не сработал, пробуем через публичный сайт
            String status = checkTrackingStatusViaPublicSite(cleanTrackingNumber);
            if (status != null && !status.isEmpty()) {
                return status;
            }
            
            // Если публичный сайт не сработал, пробуем API v2 без авторизации
            status = tryCdekApiV2(cleanTrackingNumber);
            if (status != null && !status.isEmpty()) {
                return status;
            }
            
            logger.warn("Не удалось получить статус для трекера СДЭК: {}", trackingNumber);
            return "Статус недоступен";
            
        } catch (Exception e) {
            logger.error("Ошибка при проверке статуса трекера СДЭК {}: {}", trackingNumber, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Получает токен доступа через OAuth 2.0
     */
    public String getAccessToken() {
        // Проверяем, есть ли валидный токен в кэше
        if (accessToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }
        
        try {
            logger.info("Получение токена доступа СДЭК через OAuth 2.0");
            
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
                    
                    logger.info("Токен доступа СДЭК успешно получен, действителен до {}", tokenExpiresAt);
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
     * Пробует получить статус через API v2 СДЭК с авторизацией
     * Использует метод получения интервалов доставки
     */
    private String tryCdekApiV2WithAuth(String trackingNumber) {
        try {
            String token = getAccessToken();
            if (token == null) {
                logger.debug("Не удалось получить токен доступа для трекера {}", trackingNumber);
                return null;
            }
            
            logger.info("Запрос интервалов доставки через API СДЭК с авторизацией: {}", trackingNumber);
            
            // Используем endpoint для получения интервалов доставки
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.cdek.ru")
                            .path("/v2/delivery/intervals")
                            .queryParam("cdek_number", trackingNumber)
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && !response.isEmpty()) {
                return parseDeliveryIntervalsResponse(response, trackingNumber);
            }
        } catch (Exception e) {
            logger.debug("API получения интервалов доставки не доступен для трекера {}: {}", trackingNumber, e.getMessage());
        }
        return null;
    }
    
    /**
     * Пробует получить статус через API v2 СДЭК без авторизации
     */
    private String tryCdekApiV2(String trackingNumber) {
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.cdek.ru")
                            .path("/v2/orders")
                            .queryParam("cdek_number", trackingNumber)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && !response.isEmpty()) {
                return parseCdekResponse(response, trackingNumber);
            }
        } catch (Exception e) {
            logger.debug("API v2 не доступен для трекера {}: {}", trackingNumber, e.getMessage());
        }
        return null;
    }
    
    /**
     * Альтернативный метод проверки через публичный сайт СДЭК
     */
    private String tryAlternativeMethod(String trackingNumber) {
        return checkTrackingStatusViaPublicSite(trackingNumber);
    }
    
    /**
     * Проверяет статус трекера через публичный сайт СДЭК
     * https://my.cdek.ru/lkfl/order/{trackingNumber}
     * 
     * @param trackingNumber номер трекера
     * @return информация о статусе и дате прибытия, или null в случае ошибки
     */
    public String checkTrackingStatusViaPublicSite(String trackingNumber) {
        try {
            logger.info("Проверка статуса трекера через публичный сайт СДЭК: {}", trackingNumber);
            
            String url = "https://my.cdek.ru/lkfl/order/" + trackingNumber;
            
            // Загружаем HTML страницу
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            
            // Ищем информацию о дате прибытия
            // Паттерн для поиска "Ожидается {дата}"
            String arrivalInfo = extractArrivalDate(doc);
            
            if (arrivalInfo != null && !arrivalInfo.isEmpty()) {
                logger.info("Найдена информация о прибытии для трекера {}: {}", trackingNumber, arrivalInfo);
                return arrivalInfo;
            }
            
            // Если не нашли дату прибытия, пытаемся найти общий статус
            String status = extractStatus(doc);
            if (status != null && !status.isEmpty()) {
                logger.info("Найден статус для трекера {}: {}", trackingNumber, status);
                return status;
            }
            
            logger.warn("Не удалось извлечь информацию о статусе для трекера {}", trackingNumber);
            return null;
            
        } catch (Exception e) {
            logger.error("Ошибка при проверке статуса через публичный сайт СДЭК для трекера {}: {}", 
                    trackingNumber, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Извлекает информацию о дате прибытия из HTML страницы
     */
    private String extractArrivalDate(Document doc) {
        try {
            // Сначала ищем элементы, содержащие "Ожидается"
            Elements dateElements = doc.select("*:contains(Ожидается), *:contains(ожидается)");
            for (Element element : dateElements) {
                String text = element.text();
                if (text.toLowerCase().contains("ожидается")) {
                    // Паттерн для поиска даты после "Ожидается"
                    // Примеры: "Ожидается 28 ноября", "Ожидается 28.11.2024"
                    Pattern datePattern = Pattern.compile(
                            "(?i)ожидается\\s+(\\d{1,2}\\s+(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)|\\d{1,2}\\.\\d{1,2}(?:\\.\\d{2,4})?)",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                    );
                    Matcher dateMatcher = datePattern.matcher(text);
                    if (dateMatcher.find()) {
                        String fullMatch = dateMatcher.group(0);
                        String date = dateMatcher.group(1);
                        return "Ожидается: " + date.trim();
                    }
                    
                    // Альтернативный паттерн - ищем дату рядом со словом "ожидается"
                    Pattern altPattern = Pattern.compile(
                            "(?:ожидается|Ожидается)\\s+(\\d{1,2}\\s+(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря))",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                    );
                    Matcher altMatcher = altPattern.matcher(text);
                    if (altMatcher.find()) {
                        return "Ожидается: " + altMatcher.group(1).trim();
                    }
                }
            }
            
            // Поиск по всему тексту страницы
            String pageText = doc.text();
            Pattern pattern = Pattern.compile(
                    "(?i)(?:ожидается|Ожидается)\\s+(\\d{1,2}\\s+(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря))",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            );
            
            Matcher matcher = pattern.matcher(pageText);
            if (matcher.find()) {
                String date = matcher.group(1);
                return "Ожидается: " + date.trim();
            }
            
            // Поиск по структурированным данным (классы, атрибуты)
            Elements statusElements = doc.select("[class*='status'], [class*='arrival'], [class*='date'], [class*='expected'], [data-status], [data-date]");
            for (Element element : statusElements) {
                String text = element.text();
                if (text.toLowerCase().contains("ожидается")) {
                    // Извлекаем дату из текста
                    Pattern datePattern = Pattern.compile(
                            "\\d{1,2}\\s+(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                    );
                    Matcher dateMatcher = datePattern.matcher(text);
                    if (dateMatcher.find()) {
                        return "Ожидается: " + dateMatcher.group().trim();
                    }
                }
            }
            
        } catch (Exception e) {
            logger.debug("Ошибка при извлечении даты прибытия: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Извлекает общий статус доставки из HTML страницы
     */
    private String extractStatus(Document doc) {
        try {
            // Ищем элементы со статусом
            Elements statusElements = doc.select("[class*='status'], [class*='state'], [data-status]");
            for (Element element : statusElements) {
                String text = element.text().trim();
                if (!text.isEmpty() && text.length() < 200) { // Ограничиваем длину
                    return text;
                }
            }
            
            // Ищем заголовки или важные блоки
            Elements headings = doc.select("h1, h2, h3, .title, .header");
            for (Element heading : headings) {
                String text = heading.text().trim();
                if (!text.isEmpty() && !text.toLowerCase().contains("сдэк")) {
                    return text;
                }
            }
            
        } catch (Exception e) {
            logger.debug("Ошибка при извлечении статуса: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Парсит ответ от API СДЭК
     */
    private String parseCdekResponse(String response, String trackingNumber) {
        try {
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            
            StringBuilder result = new StringBuilder();
            
            // Пробуем извлечь информацию о статусе
            String status = null;
            if (jsonResponse.has("status")) {
                status = jsonResponse.get("status").getAsString();
            } else if (jsonResponse.has("entity")) {
                JsonObject entity = jsonResponse.getAsJsonObject("entity");
                if (entity.has("status")) {
                    status = entity.get("status").getAsString();
                }
            }
            
            // Пробуем извлечь информацию о дате прибытия
            String arrivalDate = null;
            if (jsonResponse.has("delivery_date")) {
                arrivalDate = jsonResponse.get("delivery_date").getAsString();
            } else if (jsonResponse.has("entity")) {
                JsonObject entity = jsonResponse.getAsJsonObject("entity");
                if (entity.has("delivery_date")) {
                    arrivalDate = entity.get("delivery_date").getAsString();
                } else if (entity.has("planned_delivery_date")) {
                    arrivalDate = entity.get("planned_delivery_date").getAsString();
                }
            }
            
            // Пробуем извлечь информацию из массива статусов
            if (jsonResponse.has("statuses") && jsonResponse.get("statuses").isJsonArray()) {
                var statuses = jsonResponse.getAsJsonArray("statuses");
                if (statuses.size() > 0) {
                    var lastStatus = statuses.get(statuses.size() - 1).getAsJsonObject();
                    if (lastStatus.has("name")) {
                        status = lastStatus.get("name").getAsString();
                    }
                    if (lastStatus.has("date_time")) {
                        arrivalDate = lastStatus.get("date_time").getAsString();
                    }
                }
            }
            
            // Формируем результат
            if (arrivalDate != null && !arrivalDate.isEmpty()) {
                result.append("Ожидается: ").append(arrivalDate);
                if (status != null && !status.isEmpty()) {
                    result.append(" (").append(status).append(")");
                }
            } else if (status != null && !status.isEmpty()) {
                result.append(status);
            } else {
                // Если не нашли структурированные данные, возвращаем весь ответ
                logger.info("Полный ответ СДЭК для трекера {}: {}", trackingNumber, response);
                return response;
            }
            
            return result.toString();
            
        } catch (Exception e) {
            logger.warn("Ошибка парсинга ответа СДЭК: {}", e.getMessage());
            return response; // Возвращаем сырой ответ
        }
    }

    /**
     * Парсит ответ с интервалами доставки от API СДЭК
     * Формат ответа:
     * {
     *   "date_intervals": [
     *     {
     *       "date": "2019-08-24",
     *       "time_intervals": [
     *         {
     *           "start_time": "HH:mm",
     *           "end_time": "HH:mm"
     *         }
     *       ]
     *     }
     *   ]
     * }
     */
    private String parseDeliveryIntervalsResponse(String response, String trackingNumber) {
        try {
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            
            if (!jsonResponse.has("date_intervals")) {
                logger.warn("Ответ не содержит date_intervals для трекера {}", trackingNumber);
                return null;
            }
            
            JsonArray dateIntervals = jsonResponse.getAsJsonArray("date_intervals");
            
            if (dateIntervals == null || dateIntervals.size() == 0) {
                logger.info("Нет доступных интервалов доставки для трекера {}", trackingNumber);
                return "Нет доступных интервалов доставки";
            }
            
            // Берем первый доступный интервал (самый ранний)
            JsonObject firstInterval = dateIntervals.get(0).getAsJsonObject();
            String date = firstInterval.get("date").getAsString();
            
            // Форматируем дату в читаемый вид
            String formattedDate = formatDate(date);
            
            // Извлекаем временные интервалы
            StringBuilder result = new StringBuilder();
            result.append("Ожидается: ").append(formattedDate);
            
            if (firstInterval.has("time_intervals")) {
                JsonArray timeIntervals = firstInterval.getAsJsonArray("time_intervals");
                if (timeIntervals != null && timeIntervals.size() > 0) {
                    result.append(" (");
                    for (int i = 0; i < timeIntervals.size(); i++) {
                        if (i > 0) {
                            result.append(", ");
                        }
                        JsonObject timeInterval = timeIntervals.get(i).getAsJsonObject();
                        String startTime = timeInterval.get("start_time").getAsString();
                        String endTime = timeInterval.get("end_time").getAsString();
                        result.append(startTime).append("-").append(endTime);
                    }
                    result.append(")");
                }
            }
            
            // Если есть несколько дат, добавляем информацию
            if (dateIntervals.size() > 1) {
                result.append(" (еще ").append(dateIntervals.size() - 1).append(" доступных дат)");
            }
            
            logger.info("Найдены интервалы доставки для трекера {}: {}", trackingNumber, result.toString());
            return result.toString();
            
        } catch (Exception e) {
            logger.error("Ошибка парсинга интервалов доставки для трекера {}: {}", trackingNumber, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Форматирует дату из формата yyyy-MM-dd в читаемый вид
     * Например: "2019-08-24" -> "24 августа 2019"
     */
    private String formatDate(String dateStr) {
        try {
            // Парсим дату в формате yyyy-MM-dd
            String[] parts = dateStr.split("-");
            if (parts.length == 3) {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                
                String[] monthNames = {
                    "января", "февраля", "марта", "апреля", "мая", "июня",
                    "июля", "августа", "сентября", "октября", "ноября", "декабря"
                };
                
                if (month >= 1 && month <= 12) {
                    return day + " " + monthNames[month - 1] + " " + year;
                }
            }
        } catch (Exception e) {
            logger.debug("Ошибка форматирования даты {}: {}", dateStr, e.getMessage());
        }
        
        // Если не удалось отформатировать, возвращаем как есть
        return dateStr;
    }

    /**
     * Проверяет, является ли строка валидным трекером СДЭК (набор цифр)
     * @param value значение для проверки
     * @return true если это валидный трекер
     */
    public boolean isValidTrackingNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        // Трекер СДЭК обычно состоит из цифр (может быть с дефисами)
        String cleaned = value.trim().replaceAll("[\\s-]", "");
        return cleaned.matches("\\d{8,14}"); // От 8 до 14 цифр
    }
}

