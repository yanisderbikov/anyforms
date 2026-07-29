package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.payment.ProductSalesDTO;
import ru.anyforms.dto.telegram.TelegramDigestConfirmRequestDTO;
import ru.anyforms.dto.telegram.TelegramDigestDTO;
import ru.anyforms.service.payment.SalesStatsService;
import ru.anyforms.service.telegram.TelegramDigestService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pusher/telegram")
@RequiredArgsConstructor
@Tag(name = "TelegramPusher", description = "Пулл-API для сервиса telegram-pusher (авторизация по X-Auth-Token)")
public class TelegramPusherController {

    private final TelegramDigestService telegramDigestService;
    private final SalesStatsService salesStatsService;

    @Value("${service.auth.token}")
    private String serviceToken;

    @Operation(summary = "Дайджест неотправленных уведомлений (пустой orderIds — отправлять нечего)")
    @GetMapping("/pending")
    public TelegramDigestDTO pending(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        checkToken(token);
        return telegramDigestService.buildPendingDigest();
    }

    @Operation(summary = "Подтвердить, что сообщение по заказам отправлено")
    @PostMapping("/confirm")
    public Map<String, Integer> confirm(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                        @Valid @RequestBody TelegramDigestConfirmRequestDTO request) {
        checkToken(token);
        return Map.of("confirmed", telegramDigestService.confirmSent(request.getOrderIds()));
    }

    @Operation(summary = "Оплаченные продажи обучения за период (границы дат — по МСК, включительно)")
    @GetMapping("/sales")
    public List<ProductSalesDTO> sales(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        checkToken(token);
        return salesStatsService.getTrainingSales(from, to);
    }

    private void checkToken(String token) {
        if (serviceToken == null || serviceToken.isBlank()) {
            log.warn("service.auth.token не настроен — запрос пушера отклонён");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "service token is not configured");
        }
        if (!serviceToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid auth token");
        }
    }
}
