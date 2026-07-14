package ru.anyforms.service.payment.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.anyforms.dto.payment.tinkoff.TinkoffCancelRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffCancelResponse;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitResponse;
import ru.anyforms.service.payment.TinkoffService;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
class TinkoffServiceImpl implements TinkoffService {

    private final WebClient tinkoffWebClient;
    private final TinkoffTokenService tinkoffTokenService;

    @Value("${payment.tinkoff.terminal-key}")
    private String terminalKey;

    TinkoffServiceImpl(@Qualifier("tinkoffWebClient") WebClient tinkoffWebClient,
                       TinkoffTokenService tinkoffTokenService) {
        this.tinkoffWebClient = tinkoffWebClient;
        this.tinkoffTokenService = tinkoffTokenService;
    }

    @Override
    public TinkoffInitResponse init(TinkoffInitRequest request) {
        if (terminalKey == null || terminalKey.isBlank()) {
            throw new RuntimeException("Т-Касса не настроена: пустой payment.tinkoff.terminal-key");
        }
        request.setTerminalKey(terminalKey);
        request.setToken(tinkoffTokenService.sign(rootParams(request)));

        TinkoffInitResponse response = tinkoffWebClient
                .post()
                .uri("/Init")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TinkoffInitResponse.class)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("Ошибка Т-Кассы: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new RuntimeException("Не удалось создать платёж в Т-Кассе: " + ex.getMessage()));
                })
                .block();

        if (response == null) {
            throw new RuntimeException("Получили пустой ответ от Т-Кассы");
        }
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            log.error("Т-Касса отклонила Init: код {}, {} / {}",
                    response.getErrorCode(), response.getMessage(), response.getDetails());
            throw new RuntimeException("Т-Касса отклонила платёж: " + response.getMessage());
        }

        log.info("Создан платёж Т-Кассы с external ID: {}", response.getPaymentId());
        return response;
    }

    @Override
    public TinkoffCancelResponse cancel(TinkoffCancelRequest request) {
        if (terminalKey == null || terminalKey.isBlank()) {
            throw new RuntimeException("Т-Касса не настроена: пустой payment.tinkoff.terminal-key");
        }
        request.setTerminalKey(terminalKey);
        request.setToken(tinkoffTokenService.sign(cancelRootParams(request)));

        TinkoffCancelResponse response = tinkoffWebClient
                .post()
                .uri("/Cancel")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TinkoffCancelResponse.class)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("Ошибка Т-Кассы при возврате: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new RuntimeException("Не удалось выполнить возврат в Т-Кассе: " + ex.getMessage()));
                })
                .block();

        if (response == null) {
            throw new RuntimeException("Получили пустой ответ от Т-Кассы на Cancel");
        }
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            log.error("Т-Касса отклонила Cancel для платежа {}: код {}, {} / {}",
                    request.getPaymentId(), response.getErrorCode(), response.getMessage(), response.getDetails());
            throw new RuntimeException("Т-Касса отклонила возврат: "
                    + (response.getMessage() != null ? response.getMessage() : response.getErrorCode()));
        }

        log.info("Возврат по платежу Т-Кассы {}: статус {}", response.getPaymentId(), response.getStatus());
        return response;
    }

    private Map<String, String> cancelRootParams(TinkoffCancelRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("TerminalKey", request.getTerminalKey());
        params.put("PaymentId", request.getPaymentId());
        if (request.getAmount() != null) {
            params.put("Amount", String.valueOf(request.getAmount()));
        }
        return params;
    }

    private Map<String, String> rootParams(TinkoffInitRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("TerminalKey", request.getTerminalKey());
        params.put("Amount", String.valueOf(request.getAmount()));
        params.put("OrderId", request.getOrderId());
        putIfPresent(params, "Description", request.getDescription());
        putIfPresent(params, "PayType", request.getPayType());
        putIfPresent(params, "SuccessURL", request.getSuccessURL());
        putIfPresent(params, "FailURL", request.getFailURL());
        putIfPresent(params, "NotificationURL", request.getNotificationURL());
        return params;
    }

    private void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }
}
