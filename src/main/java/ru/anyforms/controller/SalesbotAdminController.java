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
import ru.anyforms.dto.salesbot.BotGroupDTO;
import ru.anyforms.dto.salesbot.BotGroupRequestDTO;
import ru.anyforms.dto.salesbot.BotRunTypeDTO;
import ru.anyforms.dto.salesbot.BotStepCreateRequestDTO;
import ru.anyforms.dto.salesbot.BotStepDTO;
import ru.anyforms.dto.salesbot.BotStepUpdateRequestDTO;
import ru.anyforms.dto.salesbot.ManualRunDTO;
import ru.anyforms.dto.salesbot.ManualRunPreviewDTO;
import ru.anyforms.dto.salesbot.PipelineDTO;
import ru.anyforms.dto.salesbot.SalesbotDTO;
import ru.anyforms.dto.salesbot.StepMoveDirection;
import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.model.salesbot.BotRunType;
import ru.anyforms.service.salesbot.BotExecutionAnalyticsService;
import ru.anyforms.service.salesbot.BotGroupAdminService;
import ru.anyforms.service.salesbot.ManualSalesbotBatchService;
import ru.anyforms.service.salesbot.PipelineDirectory;
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
        description = "Админка дрип-кампании SalesBot: группы с цепочками ботов, ручные запуски, журнал и аналитика (только ADMIN)")
public class SalesbotAdminController {

    private final BotGroupAdminService groupService;
    private final BotExecutionAnalyticsService analyticsService;
    private final SalesbotDirectory salesbotDirectory;
    private final PipelineDirectory pipelineDirectory;
    private final ManualSalesbotBatchService manualBatchService;

    // ── справочники amoCRM ──

    @Operation(summary = "Справочник типов записей журнала",
            description = "DRIP — шаг цепочки группы; остальные — служебные запуски (ручной, доставка, повтор)",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/run-types")
    public ResponseEntity<List<BotRunTypeDTO>> getSalesbotRunTypes() {
        return ResponseEntity.ok(groupService.runTypes());
    }

    @Operation(summary = "SalesBot'ы аккаунта amoCRM",
            description = "Для выбора бота по имени; кэш ~5 минут, refresh=true перечитывает из amoCRM. "
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

    // ── группы ──

    @Operation(summary = "Группы дрип-кампании с цепочками",
            description = "Каждая группа: название, воронка/статус amoCRM (bot_group) и боты по порядку (bot_sequence)",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/groups")
    public ResponseEntity<List<BotGroupDTO>> getSalesbotGroups() {
        return ResponseEntity.ok(groupService.groups());
    }

    @Operation(summary = "Создать группу",
            description = "Воронка и статус необязательны, но задаются парой. Без них группа в прогоне не участвует",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/groups")
    public ResponseEntity<BotGroupDTO> createSalesbotGroup(@Valid @RequestBody BotGroupRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(request));
    }

    @Operation(summary = "Обновить группу", description = "Название, воронка/статус, вкл/выкл, окно отправки (HH:mm по Москве)",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/groups/{id}")
    public ResponseEntity<BotGroupDTO> updateSalesbotGroup(@PathVariable("id") Long id,
                                                           @Valid @RequestBody BotGroupRequestDTO request) {
        return ResponseEntity.ok(groupService.updateGroup(id, request));
    }

    @Operation(summary = "Удалить группу",
            description = "Цепочка удаляется вместе с группой; записи журнала остаются без ссылки на группу",
            security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteSalesbotGroup(@PathVariable("id") Long id) {
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    // ── шаги цепочки ──

    @Operation(summary = "Добавить бота в конец цепочки группы",
            description = "Позиция назначается автоматически (последняя + 1). Один бот — не более одного раза на группу",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/steps")
    public ResponseEntity<BotStepDTO> createSalesbotStep(@Valid @RequestBody BotStepCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createStep(request));
    }

    @Operation(summary = "Изменить бота и задержку на шаге", description = "Группа и позиция не меняются",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/steps/{id}")
    public ResponseEntity<BotStepDTO> updateSalesbotStep(@PathVariable("id") Long id,
                                                         @Valid @RequestBody BotStepUpdateRequestDTO request) {
        return ResponseEntity.ok(groupService.updateStep(id, request));
    }

    @Operation(summary = "Сдвинуть шаг вверх/вниз",
            description = "Меняет шаг местами с соседним. Прогресс лидов считается по позициям: лид, уже прошедший "
                    + "переставленные шаги, получит их в новом порядке, но один и тот же бот ему повторно не уйдёт",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/steps/{id}/move")
    public ResponseEntity<List<BotStepDTO>> moveSalesbotStep(@PathVariable("id") Long id,
                                                             @RequestParam StepMoveDirection direction) {
        return ResponseEntity.ok(groupService.moveStep(id, direction));
    }

    @Operation(summary = "Удалить шаг цепочки", security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/steps/{id}")
    public ResponseEntity<Void> deleteSalesbotStep(@PathVariable("id") Long id) {
        groupService.deleteStep(id);
        return ResponseEntity.noContent().build();
    }

    // ── аналитика и журнал ──

    @Operation(summary = "Аналитика по шагам: где сообщения перестают доставляться",
            description = "Счётчики SUCCESS / MESSAGE_SEND_FAILED / FAILED по каждому шагу (группа или тип, позиция, бот) "
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
            @RequestParam(required = false) BotRunType type,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) BotExecutionStatus status,
            @RequestParam(required = false) Long leadId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(analyticsService.logs(type, groupId, status, leadId, from, to, page, size));
    }

    // ── ручной запуск ──

    @Operation(summary = "Последние ручные запуски",
            description = "Живые счётчики идущего запуска и итоги завершённых (в памяти процесса, до рестарта)",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/manual-runs")
    public ResponseEntity<List<ManualRunDTO>> getManualRuns() {
        return ResponseEntity.ok(manualBatchService.recent());
    }

    @Operation(summary = "Превью ручного запуска",
            description = "Сколько лидов в воронке/статусе (с учётом тега и фильтра «Розница») и сколько из них "
                    + "уже получали этого бота",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/manual-runs/preview")
    public ResponseEntity<ManualRunPreviewDTO> previewManualRun(
            @RequestParam Long pipelineId,
            @RequestParam Long statusId,
            @Parameter(description = "Бот; без него считаются только лиды в статусе, без «уже получали»")
            @RequestParam(required = false) Long botId,
            @RequestParam(required = false) String tagName,
            @Parameter(description = "true — только розница, false — только не розница, пусто — любые")
            @RequestParam(required = false) Boolean retail) {
        return ResponseEntity.ok(manualBatchService.preview(pipelineId, statusId, botId, tagName, retail));
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

    // ── ошибки ──

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

    /** Нечитаемое тело или неверный параметр (например, неизвестный тип) — 400 с понятным текстом, а не 500. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleUnreadable(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Некорректные данные запроса: проверьте тип, статус и даты."));
    }
}
