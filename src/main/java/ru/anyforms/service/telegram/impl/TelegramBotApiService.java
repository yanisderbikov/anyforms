package ru.anyforms.service.telegram.impl;

import com.google.gson.Gson;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import ru.anyforms.service.telegram.TelegramService;

import java.util.List;

@Slf4j
@Component
class TelegramBotApiService implements TelegramService {

    private static final String TELEGRAM_BASE_URL = "https://api.telegram.org";

    private final WebClient webClient;
    private final Gson gson = new Gson();

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.retail.chat.id}")
    private String retailChatId;

    TelegramBotApiService() {
        HttpClient http = HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE);
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(http))
                .baseUrl(TELEGRAM_BASE_URL)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank()
                && retailChatId != null && !retailChatId.isBlank();
    }

    @Override
    public void sendMessage(String text) {
        send(new SendMessageRequest(retailChatId, text, null));
    }

    @Override
    public void sendMessageWithUrlButton(String text, String buttonText, String buttonUrl) {
        ReplyMarkup markup = new ReplyMarkup(List.of(List.of(new InlineButton(buttonText, buttonUrl))));
        send(new SendMessageRequest(retailChatId, text, markup));
    }

    private void send(SendMessageRequest request) {
        if (!isConfigured()) {
            throw new IllegalStateException("Telegram не настроен: заполните TELEGRAM_BOT_TOKEN и TELEGRAM_RETAIL_CHAT_ID");
        }
        String json = gson.toJson(request);

        SendResult result = webClient.post()
                .uri("/bot" + botToken + "/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new SendResult(response.statusCode().value(), body)))
                .block();

        if (result == null) {
            throw new RuntimeException("Telegram: пустой ответ при отправке сообщения в чат " + retailChatId);
        }
        if (result.status() < 200 || result.status() >= 300) {
            log.error("Telegram отклонил сообщение в чат {}: HTTP {} body: {}", retailChatId, result.status(), result.body());
            throw new RuntimeException("Telegram вернул HTTP " + result.status() + ": " + result.body());
        }
        log.info("Telegram принял сообщение в чат {}: HTTP {}", retailChatId, result.status());
    }

    private record SendMessageRequest(String chat_id, String text, ReplyMarkup reply_markup) {}

    private record ReplyMarkup(List<List<InlineButton>> inline_keyboard) {}

    private record InlineButton(String text, String url) {}

    private record SendResult(int status, String body) {}
}
