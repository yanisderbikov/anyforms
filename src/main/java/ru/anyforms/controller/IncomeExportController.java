package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.service.payment.TrainingIncomeExportService;

import java.util.Map;

@RestController
@RequestMapping("/api/income-export")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "IncomeExport", description = "Выгрузка доходов обучения в гугл-таблицу")
public class IncomeExportController {

    private final TrainingIncomeExportService trainingIncomeExportService;

    @Operation(summary = "Ручной запуск выгрузки",
            description = "Та же логика, что и у ночного шедулера; возвращает число дописанных строк")
    @PostMapping("/run")
    public Map<String, Integer> run() {
        return Map.of("appendedRows", trainingIncomeExportService.export());
    }
}
