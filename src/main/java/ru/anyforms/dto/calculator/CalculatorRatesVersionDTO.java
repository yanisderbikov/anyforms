package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Версия справочника ставок в истории изменений")
public record CalculatorRatesVersionDTO(Long versionId, Instant updatedAt, String updatedBy) {
}
