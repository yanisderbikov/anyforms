package ru.anyforms.config.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class YooKassaWebClientConfig {

    @Value("${payment.yookassa.shop.id}")
    private String shopId;

    @Value("${payment.yookassa.api.key}")
    private String apiKey;

    @Bean("yooKassaWebClient")
    public WebClient yooKassaWebClient() {
        String credentials = shopId + ":" + apiKey;
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        return WebClient.builder()
                .baseUrl("https://api.yookassa.ru/v3")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
