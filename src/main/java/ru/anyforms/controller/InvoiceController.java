package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.payment.InvoiceCreateRequest;
import ru.anyforms.dto.payment.InvoiceDTO;
import ru.anyforms.service.payment.InvoiceService;

import java.util.List;

@RestController
@RequestMapping("/api/invoice")
@RequiredArgsConstructor
@Tag(name = "Invoice", description = "Ручное выставление счетов через Т-Кассу")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @Operation(summary = "Выставить счёт",
            description = "Создаёт платёж в Т-Кассе и возвращает ссылку на оплату",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping
    public ResponseEntity<InvoiceDTO> create(@Valid @RequestBody InvoiceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.create(request));
    }

    @Operation(summary = "Последние выставленные счета",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/recent")
    public ResponseEntity<List<InvoiceDTO>> recent(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(invoiceService.recent(Math.min(Math.max(limit, 1), 50)));
    }
}
