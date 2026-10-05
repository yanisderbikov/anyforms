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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.promo.PromoPopupCreateUpdateRequest;
import ru.anyforms.dto.promo.PromoPopupDTO;
import ru.anyforms.dto.promo.PromoPopupLeadDTO;
import ru.anyforms.dto.promo.PromoPopupOptionsDTO;
import ru.anyforms.service.promo.PromoPopupAdminService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/promo-popup")
@RequiredArgsConstructor
@Tag(name = "PromoPopup", description = "Админка попапов с промокодами (только ADMIN)")
public class PromoPopupController {

    private final PromoPopupAdminService promoPopupAdminService;

    @Operation(summary = "Все попапы", description = "Новые сверху, с количеством выданных кодов",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping
    public ResponseEntity<List<PromoPopupDTO>> list() {
        return ResponseEntity.ok(promoPopupAdminService.list());
    }

    @Operation(summary = "Справочники формы", description = "Ответственные и типы задач amoCRM, версия согласий",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/options")
    public ResponseEntity<PromoPopupOptionsDTO> options() {
        return ResponseEntity.ok(promoPopupAdminService.options());
    }

    @Operation(summary = "Создать попап", security = @SecurityRequirement(name = "Bearer"))
    @PostMapping
    public ResponseEntity<PromoPopupDTO> create(@Valid @RequestBody PromoPopupCreateUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promoPopupAdminService.create(request));
    }

    @Operation(summary = "Обновить попап", description = "Уже выданные коды не меняются",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/{id}")
    public ResponseEntity<PromoPopupDTO> update(@PathVariable("id") UUID id,
                                                @Valid @RequestBody PromoPopupCreateUpdateRequest request) {
        return ResponseEntity.ok(promoPopupAdminService.update(id, request));
    }

    @Operation(summary = "Удалить попап", description = "Только если по нему ещё не выдано ни одного кода",
            security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        promoPopupAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Заявки из попапов", description = "Последние выданные коды; popupId — фильтр по попапу",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/leads")
    public ResponseEntity<List<PromoPopupLeadDTO>> leads(
            @RequestParam(value = "popupId", required = false) UUID popupId,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return ResponseEntity.ok(promoPopupAdminService.leads(popupId, limit));
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
