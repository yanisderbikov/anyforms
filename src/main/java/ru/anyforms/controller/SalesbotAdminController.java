package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.RunSalesbotBatchRequestDTO;
import ru.anyforms.dto.salesbot.BotAnalyticsDTO;
import ru.anyforms.dto.salesbot.BotExecutionLogPageDTO;
import ru.anyforms.dto.salesbot.BotStepCreateRequestDTO;
import ru.anyforms.dto.salesbot.BotStepDTO;
import ru.anyforms.dto.salesbot.BotStepUpdateRequestDTO;
import ru.anyforms.dto.salesbot.FunnelDTO;
import ru.anyforms.dto.salesbot.FunnelRequestDTO;
import ru.anyforms.dto.salesbot.ManualRunDTO;
import ru.anyforms.dto.salesbot.ManualRunPreviewDTO;
import ru.anyforms.dto.salesbot.OrderTypeDTO;
import ru.anyforms.dto.salesbot.PipelineDTO;
import ru.anyforms.dto.salesbot.SalesbotDTO;
import ru.anyforms.dto.salesbot.SalesbotTypeConfigDTO;
import ru.anyforms.dto.salesbot.StepMoveDirection;
import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.service.salesbot.BotExecutionAnalyticsService;
import ru.anyforms.service.salesbot.ManualSalesbotBatchService;
import ru.anyforms.service.salesbot.PipelineDirectory;
import ru.anyforms.service.salesbot.SalesbotConfigAdminService;
import ru.anyforms.service.salesbot.SalesbotDirectory;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Админка дрип-кампании SalesBot. Доступ — только ADMIN (см. {@code WebSecurityConfig}:
 * {@code /api/salesbot/**}).
 */
@RestController
@RequestMapping("/api/salesbot/admin")
@RequiredArgsConstructor
@Tag(name = "Salesbot admin",
        description = "Админка дрип-кампании SalesBot: воронки типов, цепочки ботов, журнал и аналитика (только ADMIN)")
public class SalesbotAdminController {

    private final SalesbotConfigAdminService configService;
    private final BotExecutionAnalyticsService analyticsService;
    private final SalesbotDirectory salesbotDirectory;
    private final PipelineDirectory pipelineDirectory;
    private final ManualSalesbotBatchService manualBatchService;

    @Operation(summary = "Справочник типов заказа",
            description = "Все значения OrderType с подписями; drip=false — служебный тип без воронки и цепочки",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/order-types")
    public ResponseEntity<List<OrderTypeDTO>> getSalesbotOrderTypes() {
        return ResponseEntity.ok(configService.orderTypes());
    }

    @Operation(summary = "SalesBot'ы аккаунта amoCRM",
            description = "Для выбора бота по имени при добавлении шага; кэш ~5 минут, refresh=true перечитывает из amoCRM. "
                    + "502 — amoCRM недоступен и кэша нет",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/bots")
    public ResponseEntity<List<SalesbotDTO>> getSalesbots(@RequestParam(defaultValue = "false") boolean refresh) {
        try {
            return ResponseEntity.ok(salesbotDirectory.bots(refresh).stream().map(SalesbotDTO::from).toList());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    @Operation(summary = "Воронки и статусы аккаунта amoCRM",
            description = "Для выбора воронки/статуса по имени; кэш ~5 минут, refresh=true перечитывает. 502 — amoCRM недоступен и кэша нет",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/pipelines")
    public ResponseEntity<List<PipelineDTO>> getSalesbotPipelines(@RequestParam(defaultValue = "false") boolean refresh) {
        try {
            return ResponseEntity.ok(pipelineDirectory.pipelines(refresh).stream().map(PipelineDTO::from).toList());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    @Operation(summary = "Сводка настроек по типам",
            description = "Для каждого типа: воронка/статус amoCRM (order_type_funnel) и цепочка ботов (bot_sequence)",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/config")
    public ResponseEntity<List<SalesbotTypeConfigDTO>> getSalesbotConfig() {
        return ResponseEntity.ok(configService.config());
    }

    @Operation(summary = "Задать воронку/статус для типа",
            description = "На тип — ровно одна воронка. Без воронки тип в прогоне не обрабатывается",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/funnels")
    public ResponseEntity<FunnelDTO> createSalesbotFunnel(@Valid @RequestBody FunnelRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.createFunnel(request));
    }

    @Operation(summary = "Обновить воронку/статус", security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/funnels/{id}")
    public ResponseEntity<FunnelDTO> updateSalesbotFunnel(@PathVariable("id") Long id,
                                                          @Valid @RequestBody FunnelRequestDTO request) {
        return ResponseEntity.ok(configService.updateFunnel(id, request));
    }

    @Operation(summary = "Удалить воронку типа",
            description = "Тип перестаёт обрабатываться, цепочка ботов остаётся",
            security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/funnels/{id}")
    public ResponseEntity<Void> deleteSalesbotFunnel(@PathVariable("id") Long id) {
        configService.deleteFunnel(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Добавить бота в конец цепочки",
            description = "Позиция назначается автоматически (последняя + 1). Один бот — не более одного раза на тип",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/steps")
    public ResponseEntity<BotStepDTO> createSalesbotStep(@Valid @RequestBody BotStepCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.createStep(request));
    }

    @Operation(summary = "Заменить бота на шаге", description = "Тип и позиция не меняются",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/steps/{id}")
    public ResponseEntity<BotStepDTO> updateSalesbotStep(@PathVariable("id") Long id,
                                                         @Valid @RequestBody BotStepUpdateRequestDTO request) {
        return ResponseEntity.ok(configService.updateStep(id, request));
    }

    @Operation(summary = "Сдвинуть шаг вверх/вниз",
            description = "Меняет шаг местами с соседним. Прогресс лидов считается по позициям: лид, уже прошедший "
                    + "переставленные шаги, получит их в новом порядке, но один и тот же бот ему повторно не уйдёт",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/steps/{id}/move")
    public ResponseEntity<List<BotStepDTO>> moveSalesbotStep(@PathVariable("id") Long id,
                                                             @RequestParam StepMoveDirection direction) {
        return ResponseEntity.ok(configService.moveStep(id, direction));
    }

    @Operation(summary = "Удалить шаг цепочки", security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/steps/{id}")
    public ResponseEntity<Void> deleteSalesbotStep(@PathVariable("id") Long id) {
        configService.deleteStep(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Аналитика по шагам: где сообщения перестают доставляться",
            description = "Счётчики SUCCESS / MESSAGE_SEND_FAILED / FAILED по каждому шагу (тип, позиция, бот) "
                    + "за период. Даты — календарные дни по Москве, включительно; без дат — за всё время",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/analytics")
    public ResponseEntity<BotAnalyticsDTO> getSalesbotAnalytics(
            @Parameter(description = "Начало периода, YYYY-MM-DD (МСК)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Конец периода, YYYY-MM-DD (МСК), включительно")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(analyticsService.analytics(from, to));
    }

    @Operation(summary = "Журнал запусков ботов (bot_execution_log)",
            description = "Постранично, новые сверху. Фильтры необязательны",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/logs")
    public ResponseEntity<BotExecutionLogPageDTO> getSalesbotLogs(
            @RequestParam(required = false) OrderType type,
            @RequestParam(required = false) BotExecutionStatus status,
            @RequestParam(required = false) Long leadId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(analyticsService.logs(type, status, leadId, from, to, page, size));
    }

    @Operation(summary = "Последние ручные запуски",
            description = "Живые счётчики идущего запуска и итоги завершённых (в памяти процесса, до рестарта)",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/manual-runs")
    public ResponseEntity<List<ManualRunDTO>> getManualRuns() {
        return ResponseEntity.ok(manualBatchService.recent());
    }

    @Operation(summary = "Превью ручного запуска",
            description = "Сколько лидов в воронке/статусе (с учётом тега) и сколько из них уже получали этого бота",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/manual-runs/preview")
    public ResponseEntity<ManualRunPreviewDTO> previewManualRun(@RequestParam Long pipelineId,
                                                                @RequestParam Long statusId,
                                                                @RequestParam Long botId,
                                                                @RequestParam(required = false) String tagName) {
        return ResponseEntity.ok(manualBatchService.preview(pipelineId, statusId, botId, tagName));
    }

    @Operation(summary = "Запустить бота вручную для всех лидов воронки/статуса",
            description = "Прогон идёт в фоне; 202 с записью запуска. Лиды, уже получавшие этого бота, пропускаются. "
                    + "409 — другой ручной запуск ещё идёт",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/manual-runs")
    public ResponseEntity<ManualRunDTO> startManualRun(@Valid @RequestBody RunSalesbotBatchRequestDTO request,
                                                       Principal principal) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(manualBatchService.start(request, principal != null ? principal.getName() : null));
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

    /** Нечитаемое тело (например, неизвестный OrderType в JSON) — 400 с понятным текстом, а не 500. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleUnreadable(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Некорректные данные запроса: проверьте тип заказа, статус и даты."));
    }
}
