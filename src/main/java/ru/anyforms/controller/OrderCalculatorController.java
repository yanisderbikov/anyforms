package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
import ru.anyforms.dto.PresignUploadRequestDTO;
import ru.anyforms.dto.PresignUploadResponseDTO;
import ru.anyforms.dto.calculator.CalculatorOptionsDTO;
import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.dto.calculator.CalculatorRatesDTO;
import ru.anyforms.dto.calculator.CalculatorRatesVersionDTO;
import ru.anyforms.dto.calculator.OrderCalculationDTO;
import ru.anyforms.dto.calculator.OrderCalculationListItemDTO;
import ru.anyforms.dto.calculator.OrderCalculationRequest;
import ru.anyforms.dto.calculator.OrderCalculationResult;
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.auth.UserAccessService;
import ru.anyforms.service.calculator.CalculatorPermissions;
import ru.anyforms.service.calculator.CalculatorRatesService;
import ru.anyforms.service.calculator.CalculatorReferenceService;
import ru.anyforms.service.calculator.OrderCalculationJournalService;
import ru.anyforms.service.calculator.OrderCalculatorService;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/order-calculator")
@RequiredArgsConstructor
@Tag(name = "OrderCalculator", description = "Калькулятор заказа: цена кастомной силиконовой формы по регламенту ценообразования")
public class OrderCalculatorController {

    private final OrderCalculatorService orderCalculatorService;
    private final CalculatorRatesService calculatorRatesService;
    private final OrderCalculationJournalService orderCalculationJournalService;
    private final CalculatorReferenceService calculatorReferenceService;
    private final UserAccessService userAccessService;

    @Operation(summary = "Справочники калькулятора",
            description = "Типы форм, материалы, силикон; основатель ли текущий пользователь",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/options")
    public ResponseEntity<CalculatorOptionsDTO> options(Principal principal) {
        return ResponseEntity.ok(orderCalculatorService.options(user(principal)));
    }

    @Operation(summary = "Посчитать заказ",
            description = "Пустые технические поля калькулятор оценивает сам и помечает в sources. "
                    + "Исключения (ручные цены, комплекты, бонус, скидка на разработку, маржа ниже минимума) — только основатель, иначе 403. "
                    + "Разбивку (price, cost) получает только основатель, остальным — null",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/calculate")
    public ResponseEntity<OrderCalculationResult> calculate(@Valid @RequestBody OrderCalculationRequest request,
                                                            Principal principal) {
        UserAccess user = user(principal);
        return ResponseEntity.ok(CalculatorPermissions.visibleTo(user, orderCalculatorService.calculate(request, user)));
    }

    @Operation(summary = "Действующие ставки", description = "Только админ",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/rates")
    public ResponseEntity<CalculatorRatesDTO> rates(Principal principal) {
        requireFounder(principal);
        return ResponseEntity.ok(calculatorRatesService.get());
    }

    @Operation(summary = "Сохранить новую версию ставок",
            description = "Только админ. Каждое сохранение — новая версия, старые расчёты помнят свою",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/rates")
    public ResponseEntity<CalculatorRatesDTO> updateRates(@RequestBody CalculatorRates rates, Principal principal) {
        return ResponseEntity.ok(calculatorRatesService.update(rates, requireFounder(principal)));
    }

    @Operation(summary = "История версий ставок", description = "Только админ",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/rates/history")
    public ResponseEntity<List<CalculatorRatesVersionDTO>> ratesHistory(
            @RequestParam(value = "limit", defaultValue = "20") int limit, Principal principal) {
        requireFounder(principal);
        return ResponseEntity.ok(calculatorRatesService.history(limit));
    }

    @Operation(summary = "Presigned URL для загрузки референса",
            description = "Фото, эскиз или STL уходит из браузера сразу в S3, ключ сохраняется в позиции расчёта",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/references/presign")
    public ResponseEntity<PresignUploadResponseDTO> presignReference(@Valid @RequestBody PresignUploadRequestDTO request) {
        var presigned = calculatorReferenceService.presignUpload(request.getFilename(), request.getContentType());
        return ResponseEntity.ok(new PresignUploadResponseDTO(presigned.uploadUrl(), presigned.key()));
    }

    @Operation(summary = "Ссылки на просмотр референсов", security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/references/urls")
    public ResponseEntity<Map<String, String>> referenceUrls(@RequestBody List<String> keys) {
        return ResponseEntity.ok(calculatorReferenceService.viewUrls(keys == null ? List.of() : keys));
    }

    @Operation(summary = "Журнал расчётов", description = "Новые сверху; q — поиск по клиенту, изделиям и автору",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/calculations")
    public ResponseEntity<List<OrderCalculationListItemDTO>> calculations(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(orderCalculationJournalService.list(query, limit));
    }

    @Operation(summary = "Сохранить расчёт в журнал",
            description = "Пересчитывает по действующим ставкам и сохраняет вход и результат. "
                    + "Исключения и скидки основателя — только с комментарием",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/calculations")
    public ResponseEntity<OrderCalculationListItemDTO> save(@Valid @RequestBody OrderCalculationRequest request,
                                                            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderCalculationJournalService.save(request, user(principal)));
    }

    @Operation(summary = "Сохранённый расчёт целиком", description = "Разбивку (price, cost) получает только основатель",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/calculations/{id}")
    public ResponseEntity<OrderCalculationDTO> calculation(@PathVariable("id") Long id, Principal principal) {
        UserAccess user = user(principal);
        return ResponseEntity.ok(CalculatorPermissions.visibleTo(user, orderCalculationJournalService.get(id)));
    }

    @Operation(summary = "Удалить расчёт из журнала", description = "Только основатель",
            security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/calculations/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, Principal principal) {
        if (!CalculatorPermissions.isFounder(user(principal))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Удалять расчёты может только основатель");
        }
        orderCalculationJournalService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UserAccess requireFounder(Principal principal) {
        UserAccess user = user(principal);
        if (!CalculatorPermissions.isFounder(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ставки калькулятора доступны только админу");
        }
        return user;
    }

    private UserAccess user(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Нужно войти в админку");
        }
        return userAccessService.resolve(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Доступ отозван"));
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Некорректные данные расчёта"));
    }
}
