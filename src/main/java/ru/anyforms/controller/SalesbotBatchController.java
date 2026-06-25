package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.anyforms.dto.RunSalesbotBatchRequestDTO;
import ru.anyforms.service.salesbot.ManualSalesbotBatchRunner;

@RestController
@RequestMapping("/api/salesbot")
@RequiredArgsConstructor
@Tag(name = "Salesbot", description = "Массовый запуск SalesBot по воронке/статусу")
public class SalesbotBatchController {

    private final ManualSalesbotBatchRunner batchRunner;

    @Operation(
            summary = "Запустить SalesBot для всех лидов в заданной воронке/статусе (в фоне)",
            description = "Возвращает 202 сразу, прогон идёт асинхронно. Лиды, которым этот бот "
                    + "уже успешно запускался (есть SUCCESS в bot_execution_log), пропускаются.",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/run-batch")
    public ResponseEntity<String> runBatch(@Valid @RequestBody RunSalesbotBatchRequestDTO request) {
        batchRunner.runBatch(request.getPipelineId(), request.getStatusId(), request.getBotId());
        return ResponseEntity.accepted()
                .body("accepted: запуск SalesBot " + request.getBotId() + " запущен в фоне");
    }
}
