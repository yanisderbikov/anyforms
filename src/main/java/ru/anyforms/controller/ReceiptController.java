package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.payment.ReceiptSendRequest;
import ru.anyforms.dto.payment.ReceiptTaskDTO;
import ru.anyforms.dto.payment.ReceiptTransactionDTO;
import ru.anyforms.service.payment.ReceiptService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/receipt")
@RequiredArgsConstructor
@Tag(name = "Receipt", description = "Чеки Юкассы: отправка покупателю письма со ссылкой на чек")
public class ReceiptController {

    private final ReceiptService receiptService;

    @Operation(summary = "Отправить письмо со ссылкой на чек",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/send")
    public ResponseEntity<Void> send(@Valid @RequestBody ReceiptSendRequest request) {
        receiptService.sendReceipt(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Последние отправленные чеки со статусом таски",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/recent")
    public ResponseEntity<List<ReceiptTaskDTO>> recent(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(receiptService.recentTasks(Math.min(Math.max(limit, 1), 100)));
    }

    @Operation(summary = "Оплаченные через Юкассу покупки гайда/курса",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/transactions")
    public ResponseEntity<List<ReceiptTransactionDTO>> transactions(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(receiptService.paidTransactions(Math.min(Math.max(limit, 1), 500)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка" : e.getReason()));
    }
}
