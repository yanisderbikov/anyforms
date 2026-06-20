package ru.anyforms.service.email.impl;

import com.google.gson.Gson;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import ru.anyforms.service.email.EmailService;

import java.util.Map;

@Slf4j
@Component
class NotiSendEmailService implements EmailService {

    private static final String NOTISEND_BASE_URL = "https://api.notisend.ru/v1";

    private final WebClient webClient;
    private final Gson gson = new Gson();

    @Value("${email.notisend.api.key}")
    private String apiKey;
    @Value("${email.notisend.email.address}")
    private String emailAddress;
    @Value("${email.notisend.from.name}")
    private String fromName;

    NotiSendEmailService() {
        HttpClient http = HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE);
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(http))
                .baseUrl(NOTISEND_BASE_URL)
                .build();
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        send(new NotisendEmailRequest(emailAddress, fromName, to, subject, body, null));
    }

    @Override
    public void sendEmailWithReplyTo(String to, String subject, String body, @NonNull String replyTo) {
        send(new NotisendEmailRequest(emailAddress, fromName, to, subject, body, Map.of("Reply-To", replyTo)));
    }

    private void send(NotisendEmailRequest request) {
        String json = gson.toJson(request);

        SendResult result = webClient.post()
                .uri("/email/messages")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new SendResult(response.statusCode().value(), body)))
                .block();

        if (result == null) {
            throw new RuntimeException("NotiSend: пустой ответ при отправке письма на " + request.to());
        }
        if (result.status() < 200 || result.status() >= 300) {
            log.error("NotiSend отклонил письмо на {}: HTTP {} body: {}", request.to(), result.status(), result.body());
            throw new RuntimeException("NotiSend вернул HTTP " + result.status() + ": " + result.body());
        }
        log.info("NotiSend принял письмо на {}: HTTP {}", request.to(), result.status());
    }

    private record NotisendEmailRequest(
            String from_email,
            String from_name,
            String to,
            String subject,
            String html,
            Map<String, String> smtp_headers
    ) {}

    private record SendResult(int status, String body) {}
}
