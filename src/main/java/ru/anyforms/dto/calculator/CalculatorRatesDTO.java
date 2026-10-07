package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Действующая версия справочника ставок")
public record CalculatorRatesDTO(
        Long versionId,
        CalculatorRates rates,
        Instant updatedAt,
        @Schema(description = "Кто сохранил версию") String updatedBy) {
}
