package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.payment.PromoCodeCreateUpdateRequest;
import ru.anyforms.dto.payment.PromoCodeDTO;
import ru.anyforms.service.payment.PromoCodeAdminService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/promo-code")
@RequiredArgsConstructor
@Tag(name = "PromoCode", description = "Админка промокодов (только ADMIN)")
public class PromoCodeController {

    private final PromoCodeAdminService promoCodeAdminService;

    @Operation(summary = "Актуальные промокоды",
            description = "Все промокоды, кроме протухших (validUntil в прошлом). Новые сверху",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping
    public ResponseEntity<List<PromoCodeDTO>> list() {
        return ResponseEntity.ok(promoCodeAdminService.listNotExpired());
    }

    @Operation(summary = "Создать промокод",
            description = "Код нормализуется (trim + верхний регистр); суммы в копейках; даты ISO-8601, validUntil — исключительная граница",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping
    public ResponseEntity<PromoCodeDTO> create(@Valid @RequestBody PromoCodeCreateUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promoCodeAdminService.create(request));
    }

    @Operation(summary = "Обновить промокод",
            description = "Полное обновление всех полей промокода по id",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/{id}")
    public ResponseEntity<PromoCodeDTO> update(@PathVariable("id") UUID id,
                                               @Valid @RequestBody PromoCodeCreateUpdateRequest request) {
        return ResponseEntity.ok(promoCodeAdminService.update(id, request));
    }

    @Operation(summary = "Удалить промокод",
            description = "Историю платежей не трогает: в транзакциях код хранится строкой",
            security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        promoCodeAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Map.of("message", message.isBlank() ? "Некорректный запрос" : message));
    }
}
