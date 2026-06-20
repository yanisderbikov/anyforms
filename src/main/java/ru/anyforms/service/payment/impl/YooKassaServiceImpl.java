package ru.anyforms.service.payment.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.anyforms.dto.payment.YooKassaPaymentResponse;
import ru.anyforms.dto.payment.yookassa.CreatePaymentRequest;
import ru.anyforms.service.payment.YooKassaService;

@Service
@Slf4j
class YooKassaServiceImpl implements YooKassaService {

    private final WebClient yooKassaWebClient;
    private final IdempotenceKeyService idempotenceKeyService;

    YooKassaServiceImpl(@Qualifier("yooKassaWebClient") WebClient yooKassaWebClient,
                        IdempotenceKeyService idempotenceKeyService) {
        this.yooKassaWebClient = yooKassaWebClient;
        this.idempotenceKeyService = idempotenceKeyService;
    }

    @Override
    public YooKassaPaymentResponse createPayment(CreatePaymentRequest request) {
        try {
            String idempotenceKey = idempotenceKeyService.getOrCreateKey(request);

            YooKassaPaymentResponse response = yooKassaWebClient
                    .post()
                    .uri("/payments")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header("Idempotence-Key", idempotenceKey)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(YooKassaPaymentResponse.class)
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.error("Ошибка YooKassa API: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                        return Mono.error(new RuntimeException("Не удалось создать платёж: " + ex.getMessage()));
                    })
                    .block();

            if (response == null) {
                throw new RuntimeException("Получили пустой ответ от YooKassa API");
            }

            log.info("Создан платёж с external ID: {}", response.getId());
            return response;
        } catch (Exception e) {
            log.error("Не получилось создать платёж: {}", e.getMessage(), e);
            throw new RuntimeException("Не получилось создать платёж", e);
        }
    }
}
