package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.telegram.TelegramDigestConfirmRequestDTO;
import ru.anyforms.dto.telegram.TelegramDigestDTO;
import ru.anyforms.service.telegram.TelegramDigestService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pusher/telegram")
@RequiredArgsConstructor
@Tag(name = "TelegramPusher", description = "Пулл-API для сервиса telegram-pusher (авторизация по X-Auth-Token)")
public class TelegramPusherController {

    private final TelegramDigestService telegramDigestService;

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
