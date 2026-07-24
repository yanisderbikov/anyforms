package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.payment.TrainingInvoiceCreateRequest;
import ru.anyforms.dto.payment.TrainingInvoiceDTO;
import ru.anyforms.service.payment.InvalidPromoCodeException;
import ru.anyforms.service.payment.TrainingInvoiceService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/training-invoice")
@RequiredArgsConstructor
@Tag(name = "TrainingInvoice", description = "Счета на обучение: гайд и курсы через Юкассу")
public class TrainingInvoiceController {

    private final TrainingInvoiceService trainingInvoiceService;

    @Operation(summary = "Выставить счёт на обучение",
            description = "Создаёт платёж в Юкассе по коду продукта и возвращает ссылку на оплату",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping
    public ResponseEntity<TrainingInvoiceDTO> create(@Valid @RequestBody TrainingInvoiceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trainingInvoiceService.create(request));
    }

    @Operation(summary = "Последние счета на обучение",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/recent")
    public ResponseEntity<List<TrainingInvoiceDTO>> recent(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(trainingInvoiceService.recent(Math.min(Math.max(limit, 1), 50)));
    }

    @ExceptionHandler(InvalidPromoCodeException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPromo(InvalidPromoCodeException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
