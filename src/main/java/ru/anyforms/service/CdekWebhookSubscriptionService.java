package ru.anyforms.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

@Service
public class CdekWebhookSubscriptionService {
    private static final Logger logger = LoggerFactory.getLogger(CdekWebhookSubscriptionService.class);
    
    private static final String CDEK_WEBHOOKS_URL = "https://api.cdek.ru/v2/webhooks";
    
    private final CdekTrackingService cdekTrackingService;
    private final WebClient webClient;
    private final Gson gson;
    
    @Value("${sdek.webhook.url:}")
    private String webhookUrl;
    
    @Value("${server.port:8090}")
    private String serverPort;
    
    @Value("${sdek.auto.subscribe:true}")
    private boolean autoSubscribe;

    public CdekWebhookSubscriptionService(CdekTrackingService cdekTrackingService) {
        this.cdekTrackingService = cdekTrackingService;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.gson = new Gson();
    }

    /**
     * Автоматическая подписка на вебхуки при старте приложения
     */
    @PostConstruct
    public void init() {
        if (autoSubscribe) {
            // Задержка для того, чтобы приложение полностью запустилось
            new Thread(() -> {
                try {
                    Thread.sleep(5000); // Ждем 5 секунд после старта
                    ensureSubscription();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Ошибка при ожидании старта приложения", e);
                }
            }).start();
        }
    }

    /**
     * Проверяет наличие подписки и создает её, если необходимо
     */
    public void ensureSubscription() {
        try {
            logger.info("Проверка подписки на вебхуки СДЭК...");
            
            // Проверяем, есть ли уже подписка с нужным URL
            String hookUrl = getWebhookUrl();
            String subscriptions = getSubscriptions();
            
            if (subscriptions != null && !subscriptions.isEmpty()) {
                try {
                    JsonArray subscriptionsArray = gson.fromJson(subscriptions, JsonArray.class);
                    if (subscriptionsArray != null) {
                        for (int i = 0; i < subscriptionsArray.size(); i++) {
                            JsonObject subscription = subscriptionsArray.get(i).getAsJsonObject();
                            if (subscription.has("type") && "ORDER_STATUS".equals(subscription.get("type").getAsString())) {
                                if (subscription.has("url") && hookUrl.equals(subscription.get("url").getAsString())) {
                                    logger.info("Подписка на вебхуки СДЭК уже существует с URL: {}", hookUrl);
                                    return;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Ошибка при парсинге списка подписок, создаем новую: {}", e.getMessage());
                }
            }
            
            // Создаем подписку
            String uuid = subscribeToOrderStatus();
            if (uuid != null) {
                logger.info("Автоматическая подписка на вебхуки СДЭК успешно создана, UUID: {}", uuid);
            } else {
                logger.warn("Не удалось автоматически создать подписку на вебхуки СДЭК. " +
                           "Проверьте настройки SDEK_SECRET_KEY и SDEK_CLIENT_ID");
            }
        } catch (Exception e) {
            logger.error("Ошибка при проверке/создании подписки на вебхуки СДЭК: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Получает URL вебхука для подписки
     */
    private String getWebhookUrl() {
        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            return webhookUrl;
        }
        return "http://localhost:" + serverPort + "/webhook/cdek";
    }

    /**
     * Создает подписку на вебхуки СДЭК для получения уведомлений об изменении статуса заказа
     * @return UUID подписки или null в случае ошибки
     */
    public String subscribeToOrderStatus() {
        try {
            String token = cdekTrackingService.getAccessToken();
            if (token == null) {
                logger.error("Не удалось получить токен доступа для создания подписки на вебхуки");
                return null;
            }
            
            // Формируем URL вебхука
            String hookUrl = getWebhookUrl();
            logger.info("Создание подписки на вебхуки СДЭК: {}", hookUrl);
            
            // Формируем тело запроса
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("type", "ORDER_STATUS");
            requestBody.addProperty("url", hookUrl);
            
            String response = webClient.post()
                    .uri(CDEK_WEBHOOKS_URL)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            if (response != null && !response.isEmpty()) {
                JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                
                if (jsonResponse.has("entity")) {
                    JsonObject entity = jsonResponse.getAsJsonObject("entity");
                    if (entity.has("uuid")) {
                        String uuid = entity.get("uuid").getAsString();
                        logger.info("Подписка на вебхуки СДЭК успешно создана, UUID: {}", uuid);
                        return uuid;
                    }
                }
                
                logger.error("Неожиданный формат ответа при создании подписки: {}", response);
            }
            
        } catch (Exception e) {
            logger.error("Ошибка при создании подписки на вебхуки СДЭК: {}", e.getMessage(), e);
        }
        
        return null;
    }

    /**
     * Получает список всех активных подписок на вебхуки
     */
    public String getSubscriptions() {
        try {
            String token = cdekTrackingService.getAccessToken();
            if (token == null) {
                logger.error("Не удалось получить токен доступа для получения подписок");
                return null;
            }
            
            String response = webClient.get()
                    .uri(CDEK_WEBHOOKS_URL)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            logger.info("Список подписок на вебхуки СДЭК: {}", response);
            return response;
            
        } catch (Exception e) {
            logger.error("Ошибка при получении списка подписок: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Удаляет подписку на вебхуки по UUID
     */
    public boolean deleteSubscription(String uuid) {
        try {
            String token = cdekTrackingService.getAccessToken();
            if (token == null) {
                logger.error("Не удалось получить токен доступа для удаления подписки");
                return false;
            }
            
            String url = CDEK_WEBHOOKS_URL + "/" + uuid;
            
            webClient.delete()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            logger.info("Подписка {} успешно удалена", uuid);
            return true;
            
        } catch (Exception e) {
            logger.error("Ошибка при удалении подписки {}: {}", uuid, e.getMessage(), e);
            return false;
        }
    }
}

