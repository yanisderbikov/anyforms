package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.anyforms.dto.payment.ProductSalesDTO;
import ru.anyforms.dto.telegram.TelegramDigestConfirmRequestDTO;
import ru.anyforms.dto.telegram.TelegramDigestDTO;
import ru.anyforms.service.payment.SalesStatsService;
import ru.anyforms.service.telegram.TelegramDigestService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pusher/telegram")
@RequiredArgsConstructor
@Tag(name = "TelegramPusher", description = "Пулл-API для сервиса telegram-pusher (межсервисный токен)")
public class TelegramPusherController {

    private final TelegramDigestService telegramDigestService;
    private final SalesStatsService salesStatsService;

    @Operation(summary = "Дайджест неотправленных уведомлений (пустой orderIds — отправлять нечего)")
    @GetMapping("/pending")
    public TelegramDigestDTO pending() {
        return telegramDigestService.buildPendingDigest();
    }

    @Operation(summary = "Подтвердить, что сообщение по заказам отправлено")
    @PostMapping("/confirm")
    public Map<String, Integer> confirm(@Valid @RequestBody TelegramDigestConfirmRequestDTO request) {
        return Map.of("confirmed", telegramDigestService.confirmSent(request.getOrderIds()));
    }

    @Operation(summary = "Оплаченные продажи обучения за период (границы дат — по МСК, включительно)")
    @GetMapping("/sales")
    public List<ProductSalesDTO> sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return salesStatsService.getTrainingSales(from, to);
    }

}
