package ru.anyforms.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;

/**
 * OAuth 2.0 к API СДЭК: получение и кэширование access-токена (client_credentials).
 * Общий бин для калькулятора доставки и поиска ПВЗ — чтобы не дублировать логику авторизации.
 */
@Service
public class CdekAuthService {

    private static final Logger logger = LoggerFactory.getLogger(CdekAuthService.class);
    private static final String CDEK_AUTH_URL = "https://api.cdek.ru/v2/oauth/token";

    private final WebClient webClient;
    private final Gson gson = new Gson();

    @Value("${sdek.secret.key}")
    private String sdekSecretKey;

    @Value("${sdek.client.id}")
    private String sdekClientId;

    // Кэш токена
    private String accessToken;
    private Instant tokenExpiresAt;

    public CdekAuthService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Возвращает валидный access-токен СДЭК (из кэша или запросив новый). {@code null} при ошибке.
     */
    public synchronized String getAccessToken() {
        if (accessToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }

        try {
            String clientId = (sdekClientId != null && !sdekClientId.isEmpty()) ? sdekClientId : sdekSecretKey;
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
                JsonObject json = gson.fromJson(response, JsonObject.class);
                if (json.has("access_token")) {
                    accessToken = json.get("access_token").getAsString();
                    int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600;
                    tokenExpiresAt = Instant.now().plusSeconds(expiresIn - 60);
                    return accessToken;
                }
                logger.error("Ошибка получения токена СДЭК: {}", response);
            }
        } catch (Exception e) {
            logger.error("Ошибка при получении токена доступа СДЭК: {}", e.getMessage(), e);
        }
        return null;
    }
}
