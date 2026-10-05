package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.promo.PromoPopupActiveRequest;
import ru.anyforms.dto.promo.PromoPopupClaimRequest;
import ru.anyforms.dto.promo.PromoPopupClaimResponse;
import ru.anyforms.dto.promo.PromoPopupIssueRequest;
import ru.anyforms.dto.promo.PromoPopupViewRequest;
import ru.anyforms.dto.promo.PublicPromoPopupDTO;
import ru.anyforms.service.promo.PromoPopupPublicService;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/promo-popup")
@RequiredArgsConstructor
@Tag(name = "PublicPromoPopup", description = "Попап с персональным промокодом на витрине")
public class PublicPromoPopupController {

    private final PromoPopupPublicService promoPopupPublicService;

    @Operation(summary = "Попап для посетителя витрины",
            description = "Попап с наибольшим приоритетом, который сейчас работает в магазине и подходит посетителю "
                    + "(устройство, известные телефон и почта); 204 — показывать нечего")
    @PostMapping("/active")
    public ResponseEntity<PublicPromoPopupDTO> active(@Valid @RequestBody PromoPopupActiveRequest request) {
        return promoPopupPublicService.getActive(request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Попап показан", description = "Учитывает показ на устройстве для частоты и статистики")
    @PostMapping("/{id}/view")
    public ResponseEntity<Void> view(@PathVariable("id") UUID id, @Valid @RequestBody PromoPopupViewRequest request) {
        promoPopupPublicService.recordView(id, request.getDeviceId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Получить одноразовый код",
            description = "Генерирует код один раз на устройство; повторный запрос с того же устройства возвращает "
                    + "тот же код, пока он не использован. Код можно использовать только один раз")
    @PostMapping("/{id}/issue")
    public ResponseEntity<PromoPopupClaimResponse> issue(@PathVariable("id") UUID id,
                                                         @Valid @RequestBody PromoPopupIssueRequest request,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(promoPopupPublicService.issue(id, request,
                clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @Operation(summary = "Получить персональный промокод",
            description = "Создаёт одноразовый код на телефон и почту; повторный запрос с теми же контактами "
                    + "возвращает тот же код, пока он не использован. С одного устройства — один код на акцию")
    @PostMapping("/{id}/claim")
    public ResponseEntity<PromoPopupClaimResponse> claim(@PathVariable("id") UUID id,
                                                         @Valid @RequestBody PromoPopupClaimRequest request,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(promoPopupPublicService.claim(id, request,
                clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Не удалось выдать промокод." : e.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Map.of("message", message.isBlank() ? "Проверьте данные формы." : message));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
