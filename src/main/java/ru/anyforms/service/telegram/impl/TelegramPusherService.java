package ru.anyforms.service.telegram.impl;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.anyforms.service.telegram.TelegramService;

import java.time.Duration;

@Slf4j
@Component
class TelegramPusherService implements TelegramService {

    private final WebClient webClient = WebClient.builder().build();
    private final Gson gson = new Gson();

    @Value("${telegram.pusher.url}")
    private String pusherUrl;

    @Value("${telegram.pusher.token}")
    private String pusherToken;

    @Override
    public boolean isConfigured() {
        return pusherUrl != null && !pusherUrl.isBlank();
    }

    @Override
    public void sendMessage(String text) {
        send(new SendRequest(text, null, null));
    }

    @Override
    public void sendMessageWithUrlButton(String text, String buttonText, String buttonUrl) {
        send(new SendRequest(text, buttonText, buttonUrl));
    }

    private void send(SendRequest request) {
        if (!isConfigured()) {
            throw new IllegalStateException("Telegram-пушер не настроен: заполните TELEGRAM_PUSHER_URL");
        }
        String json = gson.toJson(request);

        SendResult result = webClient.post()
                .uri(pusherUrl.replaceAll("/+$", "") + "/send")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (pusherToken != null && !pusherToken.isBlank()) {
                        headers.set("X-Auth-Token", pusherToken);
                    }
                })
                .bodyValue(json)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new SendResult(response.statusCode().value(), body)))
                .timeout(Duration.ofSeconds(20))
                .block();

        if (result == null) {
            throw new RuntimeException("Telegram-пушер: пустой ответ при отправке сообщения");
        }
        if (result.status() < 200 || result.status() >= 300) {
            log.error("Telegram-пушер отклонил сообщение: HTTP {} body: {}", result.status(), result.body());
            throw new RuntimeException("Telegram-пушер: HTTP " + result.status());
        }
    }

    private record SendRequest(String text, String buttonText, String buttonUrl) {
    }

    private record SendResult(int status, String body) {
    }
}
