package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.delivery.FreeDeliveryDTO;
import ru.anyforms.dto.delivery.FreeDeliveryUpdateRequest;
import ru.anyforms.service.delivery.FreeDeliveryService;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/free-delivery")
@RequiredArgsConstructor
@Tag(name = "FreeDelivery", description = "Админка бесплатной доставки (только ADMIN)")
public class FreeDeliveryController {

    private final FreeDeliveryService freeDeliveryService;

    @Operation(summary = "Настройка бесплатной доставки",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping
    public ResponseEntity<FreeDeliveryDTO> get() {
        return ResponseEntity.ok(freeDeliveryService.get());
    }

    @Operation(summary = "Изменить настройку бесплатной доставки",
            description = "Порог сравнивается с суммой к оплате после промокода; действует для новых заказов",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping
    public ResponseEntity<FreeDeliveryDTO> update(@Valid @RequestBody FreeDeliveryUpdateRequest request) {
        return ResponseEntity.ok(freeDeliveryService.update(request));
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
